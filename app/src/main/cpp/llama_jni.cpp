#include <android/log.h>
#include <jni.h>

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <cstdlib>
#include <mutex>
#include <stdexcept>
#include <string>
#include <thread>
#include <unistd.h>
#include <vector>

#include "llama.h"
#include "ggml-backend.h"
#include "ggml-cpu.h"
#include "ggml-opencl.h"
#include "ggml-vulkan.h"

namespace {

constexpr const char * LOG_TAG = "AndroidLlamaJNI";
constexpr uint32_t BATCH_SIZE = 512;
constexpr int32_t REPEAT_LAST_N = 128;

std::mutex g_mutex;
std::mutex g_log_mutex;
std::atomic_bool g_cancelled{false};
llama_model * g_model = nullptr;
std::string g_recent_native_log;

void native_log_callback(enum ggml_log_level level, const char * text, void *) {
    if (text == nullptr) return;
    const int android_level = level >= GGML_LOG_LEVEL_ERROR
            ? ANDROID_LOG_ERROR
            : level >= GGML_LOG_LEVEL_WARN ? ANDROID_LOG_WARN : ANDROID_LOG_INFO;
    __android_log_print(android_level, LOG_TAG, "%s", text);
    std::lock_guard<std::mutex> lock(g_log_mutex);
    g_recent_native_log += text;
    constexpr size_t MAX_LOG_CHARS = 6000;
    if (g_recent_native_log.size() > MAX_LOG_CHARS) {
        g_recent_native_log.erase(0, g_recent_native_log.size() - MAX_LOG_CHARS);
    }
}

void log_error(const std::string & message) {
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "%s", message.c_str());
}

void log_info(const std::string & message) {
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "%s", message.c_str());
}

void throw_java(JNIEnv * env, const char * class_name, const std::string & message) {
    log_error(message);
    if (jclass exception = env->FindClass(class_name)) {
        env->ThrowNew(exception, message.c_str());
    }
}

std::string to_string(JNIEnv * env, jstring value) {
    if (value == nullptr) return {};
    const char * chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) throw std::runtime_error("Could not read Java string");
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

std::vector<std::string> to_strings(JNIEnv * env, jobjectArray values) {
    const jsize count = values == nullptr ? 0 : env->GetArrayLength(values);
    std::vector<std::string> result;
    result.reserve(static_cast<size_t>(count));
    for (jsize i = 0; i < count; ++i) {
        auto value = static_cast<jstring>(env->GetObjectArrayElement(values, i));
        result.push_back(to_string(env, value));
        env->DeleteLocalRef(value);
    }
    return result;
}

bool is_safe_accelerator(ggml_backend_dev_t device) {
    const std::string description = ggml_backend_dev_description(device) == nullptr
            ? ""
            : ggml_backend_dev_description(device);
    // The Adreno 610 driver reports Vulkan/OpenCL compute devices, but model tensor
    // initialization either dereferences an incomplete buffer interface or stalls
    // indefinitely before allocating weights. Do not expose a backend that cannot
    // safely complete model loading.
    return description.find("Adreno (TM) 610") == std::string::npos;
}

ggml_backend_reg_t registry_for_backend(const std::string & backend) {
    if (backend == "CPU") return ggml_backend_cpu_reg();
    if (backend == "VULKAN") return ggml_backend_vk_reg();
    if (backend == "OPENCL") return ggml_backend_opencl_reg();
    return nullptr;
}

std::vector<ggml_backend_dev_t> devices_for_backend(const std::string & backend) {
    std::vector<ggml_backend_dev_t> devices;
    ggml_backend_reg_t registry = registry_for_backend(backend);
    if (registry == nullptr) return devices;
    const size_t count = ggml_backend_reg_dev_count(registry);
    for (size_t index = 0; index < count; ++index) {
        ggml_backend_dev_t device = ggml_backend_reg_dev_get(registry, index);
        if (device != nullptr && (backend == "CPU" || is_safe_accelerator(device))) {
            devices.push_back(device);
        }
    }
    return devices;
}

std::vector<std::string> supported_backends() {
    std::vector<std::string> result{"CPU"};
    for (const char * candidate : {"VULKAN", "OPENCL"}) {
        if (!devices_for_backend(candidate).empty()) result.emplace_back(candidate);
    }
    return result;
}

std::string safe_device_field(const char * value) {
    std::string result = value == nullptr ? "" : value;
    std::replace(result.begin(), result.end(), '\t', ' ');
    std::replace(result.begin(), result.end(), '\n', ' ');
    return result;
}

std::vector<std::string> backend_devices() {
    std::vector<std::string> result;
    for (const char * backend : {"CPU", "VULKAN", "OPENCL"}) {
        for (ggml_backend_dev_t device : devices_for_backend(backend)) {
            size_t free_memory = 0;
            size_t total_memory = 0;
            ggml_backend_dev_memory(device, &free_memory, &total_memory);
            result.push_back(
                    std::string(backend) + "\t" +
                    safe_device_field(ggml_backend_dev_name(device)) + "\t" +
                    safe_device_field(ggml_backend_dev_description(device)) + "\t" +
                    std::to_string(total_memory));
        }
    }
    return result;
}

std::string model_metadata_value(llama_model_meta_key key) {
    const char * name = llama_model_meta_key_str(key);
    if (g_model == nullptr || name == nullptr) return {};
    std::vector<char> buffer(128);
    int32_t written = llama_model_meta_val_str(g_model, name, buffer.data(), buffer.size());
    if (written >= static_cast<int32_t>(buffer.size())) {
        buffer.resize(static_cast<size_t>(written) + 1);
        written = llama_model_meta_val_str(g_model, name, buffer.data(), buffer.size());
    }
    return written > 0 ? std::string(buffer.data(), static_cast<size_t>(written)) : std::string();
}

jobjectArray strings_to_java(JNIEnv * env, const std::vector<std::string> & values) {
    jclass string_class = env->FindClass("java/lang/String");
    if (string_class == nullptr) return nullptr;
    jobjectArray result = env->NewObjectArray(
            static_cast<jsize>(values.size()), string_class, nullptr);
    env->DeleteLocalRef(string_class);
    if (result == nullptr) return nullptr;
    for (jsize index = 0; index < static_cast<jsize>(values.size()); ++index) {
        jstring value = env->NewStringUTF(values[static_cast<size_t>(index)].c_str());
        if (value == nullptr) return nullptr;
        env->SetObjectArrayElement(result, index, value);
        env->DeleteLocalRef(value);
    }
    return result;
}

std::string format_chat(
        const std::vector<std::string> & roles,
        const std::vector<std::string> & original_contents,
        const std::string & reasoning_mode) {
    if (roles.empty() || roles.size() != original_contents.size()) {
        throw std::runtime_error("Chat messages are missing or malformed");
    }

    auto contents = original_contents;
    if (reasoning_mode == "OFF") {
        const auto latest_user = std::find(roles.rbegin(), roles.rend(), "user");
        if (latest_user != roles.rend()) {
            const size_t index = roles.size() - 1 - static_cast<size_t>(
                    std::distance(roles.rbegin(), latest_user));
            contents[index] +=
                    "\n\n/no_think\nAnswer directly. Do not output a hidden reasoning block.";
        }
    }

    const char * chat_template = llama_model_chat_template(g_model, nullptr);
    if (chat_template != nullptr) {
        std::vector<llama_chat_message> messages;
        messages.reserve(roles.size());
        for (size_t i = 0; i < roles.size(); ++i) {
            messages.push_back({roles[i].c_str(), contents[i].c_str()});
        }

        int32_t required = llama_chat_apply_template(
                chat_template, messages.data(), messages.size(), true, nullptr, 0);
        if (required < 0) throw std::runtime_error("The model chat template is not supported");
        std::vector<char> buffer(static_cast<size_t>(required) + 1);
        const int32_t written = llama_chat_apply_template(
                chat_template,
                messages.data(),
                messages.size(),
                true,
                buffer.data(),
                static_cast<int32_t>(buffer.size()));
        if (written < 0) throw std::runtime_error("Could not apply the model chat template");
        return {buffer.data(), static_cast<size_t>(written)};
    }

    std::string prompt;
    for (size_t i = 0; i < roles.size(); ++i) {
        if (roles[i] == "assistant") prompt += "Assistant: ";
        else if (roles[i] == "system") prompt += "System: ";
        else prompt += "User: ";
        prompt += contents[i];
        prompt += '\n';
    }
    prompt += "Assistant: ";
    return prompt;
}

std::vector<llama_token> tokenize(const std::string & prompt, bool add_special = true) {
    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    int32_t count = llama_tokenize(
            vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()), nullptr, 0, add_special, true);
    if (count == INT32_MIN) throw std::runtime_error("Prompt is too large to tokenize");
    if (count < 0) count = -count;
    std::vector<llama_token> tokens(static_cast<size_t>(count));
    count = llama_tokenize(
            vocab,
            prompt.c_str(),
            static_cast<int32_t>(prompt.size()),
            tokens.data(),
            static_cast<int32_t>(tokens.size()),
            add_special,
            true);
    if (count < 0) throw std::runtime_error("Could not tokenize the conversation");
    tokens.resize(static_cast<size_t>(count));
    return tokens;
}

std::string token_piece(const llama_vocab * vocab, llama_token token) {
    std::vector<char> buffer(32);
    int32_t count = llama_token_to_piece(
            vocab, token, buffer.data(), static_cast<int32_t>(buffer.size()), 0, false);
    if (count < 0) {
        buffer.resize(static_cast<size_t>(-count));
        count = llama_token_to_piece(
                vocab, token, buffer.data(), static_cast<int32_t>(buffer.size()), 0, false);
    }
    if (count < 0) throw std::runtime_error("Could not decode a generated token");
    return {buffer.data(), static_cast<size_t>(count)};
}

size_t complete_utf8_prefix(const std::string & text) {
    size_t index = 0;
    while (index < text.size()) {
        const auto lead = static_cast<uint8_t>(text[index]);
        size_t length = 0;
        if (lead <= 0x7f) length = 1;
        else if (lead >= 0xc2 && lead <= 0xdf) length = 2;
        else if (lead >= 0xe0 && lead <= 0xef) length = 3;
        else if (lead >= 0xf0 && lead <= 0xf4) length = 4;
        else throw std::runtime_error("The model generated invalid UTF-8");

        if (index + length > text.size()) break;
        for (size_t offset = 1; offset < length; ++offset) {
            const auto next = static_cast<uint8_t>(text[index + offset]);
            if ((next & 0xc0) != 0x80) {
                throw std::runtime_error("The model generated invalid UTF-8");
            }
        }
        if (length == 3) {
            const auto second = static_cast<uint8_t>(text[index + 1]);
            if ((lead == 0xe0 && second < 0xa0) || (lead == 0xed && second >= 0xa0)) {
                throw std::runtime_error("The model generated invalid UTF-8");
            }
        } else if (length == 4) {
            const auto second = static_cast<uint8_t>(text[index + 1]);
            if ((lead == 0xf0 && second < 0x90) || (lead == 0xf4 && second >= 0x90)) {
                throw std::runtime_error("The model generated invalid UTF-8");
            }
        }
        index += length;
    }
    return index;
}

jstring utf8_to_java(JNIEnv * env, const std::string & text) {
    std::vector<jchar> utf16;
    utf16.reserve(text.size());
    size_t index = 0;
    while (index < text.size()) {
        const auto lead = static_cast<uint8_t>(text[index]);
        uint32_t codepoint;
        size_t length;
        if (lead <= 0x7f) {
            codepoint = lead;
            length = 1;
        } else if (lead <= 0xdf) {
            codepoint = lead & 0x1f;
            length = 2;
        } else if (lead <= 0xef) {
            codepoint = lead & 0x0f;
            length = 3;
        } else {
            codepoint = lead & 0x07;
            length = 4;
        }
        for (size_t offset = 1; offset < length; ++offset) {
            codepoint = (codepoint << 6) |
                    (static_cast<uint8_t>(text[index + offset]) & 0x3f);
        }
        if (codepoint <= 0xffff) {
            utf16.push_back(static_cast<jchar>(codepoint));
        } else {
            codepoint -= 0x10000;
            utf16.push_back(static_cast<jchar>(0xd800 + (codepoint >> 10)));
            utf16.push_back(static_cast<jchar>(0xdc00 + (codepoint & 0x3ff)));
        }
        index += length;
    }
    return env->NewString(utf16.data(), static_cast<jsize>(utf16.size()));
}

bool abort_requested(void *) {
    return g_cancelled.load(std::memory_order_relaxed);
}

void decode_prompt(llama_context * context, std::vector<llama_token> & tokens) {
    for (size_t offset = 0; offset < tokens.size(); offset += BATCH_SIZE) {
        if (g_cancelled.load(std::memory_order_relaxed)) return;
        const int32_t count = static_cast<int32_t>(
                std::min<size_t>(BATCH_SIZE, tokens.size() - offset));
        llama_batch batch = llama_batch_get_one(tokens.data() + offset, count);
        const int32_t status = llama_decode(context, batch);
        if (status != 0) {
            throw std::runtime_error("Prompt evaluation failed (code " + std::to_string(status) + ")");
        }
    }
}

} // namespace

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *, void *) {
    setenv(
            "OCL_ICD_FILENAMES",
            "libOpenCL.so:"
            "libGLES_mali.so:"
            "/vendor/lib64/libOpenCL.so:"
            "/system/vendor/lib64/libOpenCL.so:"
            "/vendor/lib64/libGLES_mali.so:"
            "/vendor/lib64/egl/libGLES_mali.so:"
            "/system/vendor/lib64/egl/libGLES_mali.so",
            0);
    llama_backend_init();
    llama_log_set(native_log_callback, nullptr);
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *, void *) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    llama_backend_free();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_androidllama_inference_LlamaNative_loadModel(
        JNIEnv * env,
        jobject,
        jstring path,
        jstring backend_value,
        jint requested_gpu_layers,
        jboolean use_memory_mapping) {
    std::lock_guard<std::mutex> lock(g_mutex);
    try {
        const std::string model_path = to_string(env, path);
        if (model_path.empty()) throw std::runtime_error("Model path is empty");
        const std::string backend = to_string(env, backend_value);
        auto backend_devices = devices_for_backend(backend);
        if (backend_devices.empty()) {
            throw std::runtime_error(backend + " is not supported by this device or app build");
        }

        g_cancelled.store(false, std::memory_order_relaxed);
        {
            std::lock_guard<std::mutex> log_lock(g_log_mutex);
            g_recent_native_log.clear();
        }
        if (g_model != nullptr) {
            llama_model_free(g_model);
            g_model = nullptr;
        }

        llama_model_params params = llama_model_default_params();
        backend_devices.push_back(nullptr);
        params.devices = backend_devices.data();
        params.n_gpu_layers = backend == "CPU" ? 0 : std::max<jint>(0, requested_gpu_layers);
        params.split_mode = LLAMA_SPLIT_MODE_NONE;
        params.load_mode = use_memory_mapping == JNI_TRUE
                ? LLAMA_LOAD_MODE_MMAP
                : LLAMA_LOAD_MODE_NONE;
        log_info(
                "Loading model with backend=" + backend +
                ", gpu_layers=" + std::to_string(params.n_gpu_layers));
        g_model = llama_model_load_from_file(model_path.c_str(), params);
        if (g_model == nullptr) {
            std::lock_guard<std::mutex> log_lock(g_log_mutex);
            const std::string details = g_recent_native_log.empty()
                    ? ""
                    : ": " + g_recent_native_log;
            throw std::runtime_error("llama.cpp could not load this GGUF model" + details);
        }

        std::vector<char> description(256);
        const int32_t length = llama_model_desc(g_model, description.data(), description.size());
        const std::string result = length > 0
                ? std::string(description.data(), static_cast<size_t>(length))
                : std::string("GGUF model");
        return env->NewStringUTF(result.c_str());
    } catch (const std::exception & error) {
        if (g_model != nullptr) {
            llama_model_free(g_model);
            g_model = nullptr;
        }
        throw_java(env, "java/lang/IllegalStateException", error.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_androidllama_inference_LlamaNative_unloadModel(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_cancelled.store(true, std::memory_order_relaxed);
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_androidllama_inference_LlamaNative_cancel(JNIEnv *, jobject) {
    g_cancelled.store(true, std::memory_order_relaxed);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_example_androidllama_inference_LlamaNative_supportedBackends(JNIEnv * env, jobject) {
    return strings_to_java(env, supported_backends());
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_example_androidllama_inference_LlamaNative_backendDevices(JNIEnv * env, jobject) {
    return strings_to_java(env, backend_devices());
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_example_androidllama_inference_LlamaNative_loadedModelProfile(JNIEnv * env, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_model == nullptr) {
        throw_java(env, "java/lang/IllegalStateException", "No model is loaded");
        return nullptr;
    }
    return strings_to_java(env, {
            llama_model_chat_template(g_model, nullptr) == nullptr ? "0" : "1",
            model_metadata_value(LLAMA_MODEL_META_KEY_SAMPLING_TEMP),
            model_metadata_value(LLAMA_MODEL_META_KEY_SAMPLING_TOP_P),
            model_metadata_value(LLAMA_MODEL_META_KEY_SAMPLING_TOP_K),
            model_metadata_value(LLAMA_MODEL_META_KEY_SAMPLING_MIN_P),
            model_metadata_value(LLAMA_MODEL_META_KEY_SAMPLING_PENALTY_REPEAT),
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_androidllama_inference_LlamaNative_generate(
        JNIEnv * env,
        jobject,
        jobjectArray role_values,
        jobjectArray content_values,
        jint requested_threads,
        jint requested_context_size,
        jint requested_max_tokens,
        jfloat requested_temperature,
        jfloat requested_top_p,
        jint requested_top_k,
        jfloat requested_min_p,
        jfloat requested_repeat_penalty,
        jint requested_seed,
        jstring requested_reasoning_mode,
        jint requested_reasoning_budget,
        jobject callback) {
    std::lock_guard<std::mutex> lock(g_mutex);
    llama_context * context = nullptr;
    llama_sampler * sampler = nullptr;

    try {
        if (g_model == nullptr) throw std::runtime_error("No model is loaded");
        if (callback == nullptr) throw std::runtime_error("Token callback is missing");

        g_cancelled.store(false, std::memory_order_relaxed);
        const auto roles = to_strings(env, role_values);
        const auto contents = to_strings(env, content_values);
        const std::string reasoning_mode = to_string(env, requested_reasoning_mode);
        const std::string prompt = format_chat(roles, contents, reasoning_mode);
        auto tokens = tokenize(prompt);
        const int32_t context_size = std::clamp<int32_t>(requested_context_size, 512, 131072);
        const int32_t max_tokens = std::clamp<int32_t>(requested_max_tokens, 1, context_size);
        const int32_t answer_reserve = std::min<int32_t>(256, max_tokens / 2);
        const int32_t reasoning_budget = reasoning_mode == "BRIEF" || reasoning_mode == "OFF"
                ? std::clamp<int32_t>(
                        requested_reasoning_budget,
                        1,
                        std::max<int32_t>(1, max_tokens - answer_reserve))
                : -1;
        const float temperature = std::clamp<float>(requested_temperature, 0.0f, 2.0f);
        const float top_p = std::clamp<float>(requested_top_p, 0.0f, 1.0f);
        const int32_t top_k = std::max<int32_t>(requested_top_k, 1);
        const float min_p = std::clamp<float>(requested_min_p, 0.0f, 1.0f);
        const float repeat_penalty = std::clamp<float>(requested_repeat_penalty, 0.0f, 10.0f);
        const uint32_t seed = requested_seed < 0
                ? LLAMA_DEFAULT_SEED
                : static_cast<uint32_t>(requested_seed);
        if (tokens.empty()) throw std::runtime_error("The conversation produced an empty prompt");
        if (tokens.size() + 16 >= static_cast<size_t>(context_size)) {
            throw std::runtime_error(
                    "Conversation is too long for the " + std::to_string(context_size) + "-token context");
        }

        llama_context_params context_params = llama_context_default_params();
        context_params.n_ctx = static_cast<uint32_t>(context_size);
        context_params.n_batch = std::min<uint32_t>(BATCH_SIZE, context_params.n_ctx);
        context_params.n_ubatch = context_params.n_batch;
        context_params.n_threads = std::clamp<int32_t>(requested_threads, 1, 32);
        context_params.n_threads_batch = context_params.n_threads;
        context_params.abort_callback = abort_requested;
        context = llama_init_from_model(g_model, context_params);
        if (context == nullptr) throw std::runtime_error("Could not allocate the model context");

        decode_prompt(context, tokens);
        if (g_cancelled.load(std::memory_order_relaxed)) {
            llama_free(context);
            return;
        }

        const llama_vocab * vocab = llama_model_get_vocab(g_model);
        llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
        sampler = llama_sampler_chain_init(sampler_params);
        llama_sampler_chain_add(
                sampler,
                llama_sampler_init_penalties(
                        llama_vocab_n_tokens(vocab),
                        std::min<int32_t>(REPEAT_LAST_N, context_size),
                        repeat_penalty,
                        0.0f,
                        0.0f));
        llama_sampler_chain_add(sampler, llama_sampler_init_top_k(top_k));
        llama_sampler_chain_add(sampler, llama_sampler_init_top_p(top_p, 1));
        llama_sampler_chain_add(sampler, llama_sampler_init_min_p(min_p, 1));
        llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(sampler, llama_sampler_init_dist(seed));

        jclass callback_class = env->GetObjectClass(callback);
        jmethodID on_token = env->GetMethodID(callback_class, "onToken", "(Ljava/lang/String;)Z");
        env->DeleteLocalRef(callback_class);
        if (on_token == nullptr) throw std::runtime_error("Token callback method was not found");

        const int32_t available = context_size - static_cast<int32_t>(tokens.size());
        const int32_t limit = std::min(max_tokens, available);
        std::string pending_utf8;
        std::string generated_text;
        bool reasoning_started = false;
        bool reasoning_finished = false;
        int32_t reasoning_tokens = 0;
        std::vector<llama_token> forced_close_tokens;
        size_t forced_close_index = 0;
        for (int32_t generated = 0; generated < limit; ++generated) {
            if (g_cancelled.load(std::memory_order_relaxed)) break;

            const bool forcing_reasoning_close = forced_close_index < forced_close_tokens.size();
            const llama_token token = forcing_reasoning_close
                    ? forced_close_tokens[forced_close_index++]
                    : llama_sampler_sample(sampler, context, -1);
            if (forcing_reasoning_close) llama_sampler_accept(sampler, token);
            if (llama_vocab_is_eog(vocab, token)) break;

            const std::string piece = token_piece(vocab, token);
            if (!piece.empty()) {
                generated_text += piece;
                if (!reasoning_started && generated_text.find("<think>") != std::string::npos) {
                    reasoning_started = true;
                    reasoning_tokens = 0;
                } else if (reasoning_started && !reasoning_finished && !forcing_reasoning_close) {
                    ++reasoning_tokens;
                }
                if (reasoning_started &&
                    generated_text.find("</think>") != std::string::npos) {
                    reasoning_finished = true;
                }
                if (reasoning_budget > 0 && reasoning_started && !reasoning_finished &&
                    reasoning_tokens >= reasoning_budget && forced_close_tokens.empty()) {
                    forced_close_tokens = tokenize("</think>\n", false);
                }
                pending_utf8 += piece;
                const size_t ready = complete_utf8_prefix(pending_utf8);
                if (ready > 0) {
                    jstring java_piece = utf8_to_java(env, pending_utf8.substr(0, ready));
                    if (java_piece == nullptr) throw std::runtime_error("Could not allocate generated text");
                    const jboolean keep_going = env->CallBooleanMethod(callback, on_token, java_piece);
                    env->DeleteLocalRef(java_piece);
                    if (env->ExceptionCheck()) throw std::runtime_error("Token callback failed");
                    pending_utf8.erase(0, ready);
                    if (!keep_going) {
                        g_cancelled.store(true, std::memory_order_relaxed);
                        break;
                    }
                }
            }

            llama_token decoded_token = token;
            llama_batch batch = llama_batch_get_one(&decoded_token, 1);
            const int32_t status = llama_decode(context, batch);
            if (status != 0 && status != 2) {
                throw std::runtime_error("Token evaluation failed (code " + std::to_string(status) + ")");
            }
            if (status == 2) break;
        }

        llama_sampler_free(sampler);
        llama_free(context);
    } catch (const std::exception & error) {
        if (sampler != nullptr) llama_sampler_free(sampler);
        if (context != nullptr) llama_free(context);
        if (!env->ExceptionCheck()) {
            throw_java(env, "java/lang/IllegalStateException", error.what());
        }
    }
}

package rf.androidovshchik.vk_bot_android_sdk.utils.vkapi.calls;

import com.google.gson.Gson;

import org.json.JSONObject;

import java.util.Map;
import java.util.Objects;

import rf.androidovshchik.vk_bot_android_sdk.callbacks.Callback;

/**
 * Deserialized class of call to vk api using execute method
 */
public class CallAsync extends Call {

    private Callback<Object> callback;

    public CallAsync(String methodName, JSONObject params, Callback<Object> callback) {
        this.methodName = methodName;
        this.params = params;
        this.callback = callback;
    }

    public Callback<Object> getCallback() {
        return callback;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CallAsync)) return false;
        CallAsync call = (CallAsync) o;
        Gson gson = new Gson();
        return Objects.equals(getMethodName(), call.getMethodName()) &&
            Objects.equals(gson.fromJson(getParams().toString(), Map.class),
                gson.fromJson(call.getParams().toString(), Map.class)) &&
            Objects.equals(getCallback(), call.getCallback());
    }
}
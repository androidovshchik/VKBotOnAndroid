package rf.androidovshchik.vk_bot_android_sdk.utils.vkapi.calls;

import com.google.gson.Gson;

import org.json.JSONObject;

import java.util.Map;
import java.util.Objects;

/**
 * Deserialized class of call to vk api using execute method
 */
public class CallSync extends Call {

    public CallSync(String methodName, JSONObject params) {
        this.methodName = methodName;
        this.params = params;
    }

    @Override
    public String toString() {
        return "Call{" +
                "methodName='" + methodName + '\'' +
                ", params=" + params.toString() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CallSync)) return false;
        CallSync call = (CallSync) o;
        Gson gson = new Gson();
        return Objects.equals(getMethodName(), call.getMethodName()) &&
                Objects.equals(gson.fromJson(getParams().toString(), Map.class),
                    gson.fromJson(call.getParams().toString(), Map.class));
    }
}

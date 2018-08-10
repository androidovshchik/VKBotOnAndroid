package rf.androidovshchik.vk_bot_android_sdk.utils.vkapi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import rf.androidovshchik.vk_bot_android_sdk.utils.vkapi.calls.Call;
import rf.androidovshchik.vk_bot_android_sdk.utils.vkapi.calls.CallAsync;
import rf.androidovshchik.vk_bot_android_sdk.utils.vkapi.calls.CallSync;
import rf.androidovshchik.vk_bot_android_sdk.utils.web.Connection;
import timber.log.Timber;

import static rf.androidovshchik.vk_bot_android_sdk.clients.Client.scheduler;

/**
 * Best way to use VK API: you can call up to 25 vk api methods by call execute once
 * Because without execute you only can call up to 3 methods per second
 * <p>
 * See more: <a href="https://vk.com/dev/execute">link</a>
 */
public class Executor {

    public static boolean LOG_REQUESTS = false;

    /**
     * We can call 'execute' method no more than three times per second.
     * 1000/3 ~ 333 milliseconds
     */
    private static final int delay = 335;

    /**
     * Queue of requests
     */
    private volatile List<CallAsync> queue = new ArrayList<>();

    private final String URL = "https://api.vk.com/method/execute";
    private final String accessToken;
    private final String V = "&v=" + 5.69;


    /**
     * Init executor
     * <p>
     * All requests called directly by using client.api().call(...)
     * or indirectly (by calling methods that will use VK API) will be queued.
     * Every 350 milliseconds first 25 requests from queue will be executed.
     * VK response will be returned to the callback.
     * <p>
     * Important:
     *
     * @param accessToken Method 'execute' will be called using this access_token.
     * @see API#callSync(String, Object) requests made by this method wont be queued, be careful.
     * And responses of callSync seems like {"response":{...}} and all are instances of JSONObject.
     * but from method 'execute' will be returned "response" object directly (can be integer, boolean etc).
     */
    public Executor(String accessToken) {
        this.accessToken = "&access_token=" + accessToken;

        scheduler.scheduleWithFixedDelay(() -> {
            try {
                executing();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }, 0, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * Method that makes 'execute' requests
     * with first 25 calls from queue.
     */
    private void executing() throws JSONException {

        List<CallAsync> tmpQueue = new ArrayList<>();
        int count = 0;

        for (Iterator<CallAsync> iterator = queue.iterator(); iterator.hasNext() && count < 25; count++) {
            tmpQueue.add(iterator.next());
        }

        queue.removeAll(tmpQueue);

        StringBuilder calls = new StringBuilder();
        calls.append('[');

        for (int i = 0; i < count; i++) {
            String codeTmp = codeForExecute(tmpQueue.get(i));
            calls.append(codeTmp);
            if (i < count - 1) {
                calls.append(',');
            }
        }
        calls.append(']');

        String code = calls.toString();

        // Execute
        if (count > 0) {
            String vkCallParams = "code=return " + code + ";" + accessToken + V;

            String responseString = Connection.postRequestResponse(URL, vkCallParams);

            if (LOG_REQUESTS) {
                Timber.e("New executing request response: {} %s", responseString);
            }

            JSONObject response;

            try {
                response = new JSONObject(responseString);
            } catch (JSONException e) {
                for (CallAsync call : tmpQueue) {
                    call.getCallback().onResult("false");
                }
                Timber.e("Bad response from executing: {}, params: {} %s %s", responseString, vkCallParams);
                return;
            }

            if (response.has("execute_errors")) {
                try {
                    Timber.e("Errors when executing: {}, code: {} %s %s", response.get("execute_errors").toString(), URLDecoder.decode(code, "UTF-8"));
                } catch (UnsupportedEncodingException ignored) {
                }
            }

            if (!response.has("response")) {
                Timber.e("No 'response' object when executing code, VK response: {} %s", response.toString());
                for (CallAsync call : tmpQueue) {
                    call.getCallback().onResult("false");
                }
                return;
            }

            JSONArray responses = response.getJSONArray("response");

            for (int i = 0; i < count; i++) {
                tmpQueue.get(i).getCallback().onResult(responses.get(i));
            }
        }
    }

    /**
     * Method that makes string in json format from call object.
     *
     * @param call Call object
     * @return String 'API.method.name({param:value})'
     * @see Call
     * @see CallAsync
     * @see CallSync
     */
    public String codeForExecute(Call call) {

        return "API." + call.getMethodName() + '(' + call.getParams().toString() + ')';
    }

    /**
     * Method that puts all requests in a queue.
     *
     * @param call Call to be executed.
     */
    public void execute(CallAsync call) {
        queue.add(call);
    }
}

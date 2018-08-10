package rf.androidovshchik.vk_bot_android_sdk.callbacks;

/**
 * Created by PeterSamokhin on 29/09/2017 00:27
 */
public interface CallbackTriple<T, M, R> extends AbstractCallback {

    void onEvent(T t, M m, R r);
}

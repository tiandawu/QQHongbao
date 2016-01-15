package com.cqupt.qqhongbao;

import android.accessibilityservice.AccessibilityService;
import android.annotation.TargetApi;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by tiandawu on 2016/1/7.
 */
public class QQHongbaoService extends AccessibilityService {

    private static final String WECHAT_OPEN_EN = "Open";
    private static final String WECHAT_OPENED_EN = "You've opened";
    private final static String QQ_DEFAULT_CLICK_OPEN = "点击拆开";
//    private final static String QQ_DEFAULT_HAVE_OPENED = "已拆开";

    private final static String QQ_HONG_BAO_PASSWORD = "口令红包";
    private final static String QQ_CLICK_TO_PASTE_PASSWORD = "点击输入口令";


    private final String TAG = "tiandawu";

    private boolean mLuckyMoneyReceived;
    private String lastFetchedHongbaoId = null;
    private long lastFetchedTime = 0;
    private static final int MAX_CACHE_TOLERANCE = 5000;

    private AccessibilityNodeInfo rootNodeInfo;
    private List<AccessibilityNodeInfo> mReceiveNode;


    @TargetApi(Build.VERSION_CODES.KITKAT)
    public void recycle(AccessibilityNodeInfo info) {
        if (info.getChildCount() == 0) {
//            Log.e(TAG, "child widget----------------------------" + info.getClassName());
//            Log.e(TAG, "showDialog:" + info.canOpenPopup());
//            Log.e(TAG, "Text：" + info.getText());
//            Log.e(TAG, "windowId:" + info.getWindowId());

            if (info.getText() != null && info.getText().toString().equals(QQ_CLICK_TO_PASTE_PASSWORD)) {

                info.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);

            }

            if (info.getClassName().toString().equals("android.widget.Button") && info.getText().toString().equals("发送")) {
                info.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }

        } else {
            for (int i = 0; i < info.getChildCount(); i++) {
                if (info.getChild(i) != null) {
                    recycle(info.getChild(i));
                }
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

        this.rootNodeInfo = event.getSource();

        if (rootNodeInfo == null) {
            return;
        }

        mReceiveNode = null;

        checkNodeInfo();


        /* 如果已经接收到红包并且还没有戳开 */
        if (mLuckyMoneyReceived && (mReceiveNode != null)) {
            int size = mReceiveNode.size();
            if (size > 0) {

                String id = getHongbaoText(mReceiveNode.get(size - 1));

                long now = System.currentTimeMillis();

                if (this.shouldReturn(id, now - lastFetchedTime))
                    return;

                lastFetchedHongbaoId = id;
                lastFetchedTime = now;

                AccessibilityNodeInfo cellNode = mReceiveNode.get(size - 1);

                if (cellNode.getText().toString().equals("口令红包已拆开")) {
                    return;
                }

                cellNode.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
//                Log.e(TAG, "---------开始----------");

                if (cellNode.getText().toString().equals(QQ_HONG_BAO_PASSWORD)) {

//                    Log.e(TAG, "///////////");
                    AccessibilityNodeInfo rowNode = getRootInActiveWindow();
                    if (rowNode == null) {
                        Log.e(TAG, "noteInfo is　null");
                        return;
                    } else {
                        recycle(rowNode);
                    }
                }

//                Log.e(TAG, "-----------结束------------");
//                Log.e(TAG, "text = " + cellNode.getText().toString());
                mLuckyMoneyReceived = false;

            }
        }

    }


    /**
     * 检查节点信息
     */
    private void checkNodeInfo() {

        if (rootNodeInfo == null) {
            return;
        }

         /* 聊天会话窗口，遍历节点匹配“点击拆开”，“口令红包”，“点击输入口令” */
        List<AccessibilityNodeInfo> nodes1 = this.findAccessibilityNodeInfosByTexts(this.rootNodeInfo, new String[]{
                QQ_DEFAULT_CLICK_OPEN, QQ_HONG_BAO_PASSWORD, QQ_CLICK_TO_PASTE_PASSWORD, "发送"});

        if (!nodes1.isEmpty()) {
            String nodeId = Integer.toHexString(System.identityHashCode(this.rootNodeInfo));
            if (!nodeId.equals(lastFetchedHongbaoId)) {
                mLuckyMoneyReceived = true;
                mReceiveNode = nodes1;
            }
            return;
        }

    }


    /**
     * 将节点对象的id和红包上的内容合并
     * 用于表示一个唯一的红包
     *
     * @param node 任意对象
     * @return 红包标识字符串
     */
    private String getHongbaoText(AccessibilityNodeInfo node) {
        /* 获取红包上的文本 */
        String content;
        try {
            AccessibilityNodeInfo i = node.getParent().getChild(0);
            content = i.getText().toString();
        } catch (NullPointerException npe) {
            return null;
        }

        return content;
    }


    /**
     * 判断是否返回,减少点击次数
     * 现在的策略是当红包文本和缓存不一致时,戳
     * 文本一致且间隔大于MAX_CACHE_TOLERANCE时,戳
     *
     * @param id       红包id
     * @param duration 红包到达与缓存的间隔
     * @return 是否应该返回
     */
    private boolean shouldReturn(String id, long duration) {
        // ID为空
        if (id == null) return true;

        // 名称和缓存不一致
        if (duration < MAX_CACHE_TOLERANCE && id.equals(lastFetchedHongbaoId)) {
            return true;
        }

        return false;
    }

    /**
     * 批量化执行AccessibilityNodeInfo.findAccessibilityNodeInfosByText(text).
     * 由于这个操作影响性能,将所有需要匹配的文字一起处理,尽早返回
     *
     * @param nodeInfo 窗口根节点
     * @param texts    需要匹配的字符串们
     * @return 匹配到的节点数组
     */
    private List<AccessibilityNodeInfo> findAccessibilityNodeInfosByTexts(AccessibilityNodeInfo nodeInfo, String[] texts) {
        for (String text : texts) {
            if (text == null) continue;

            List<AccessibilityNodeInfo> nodes = nodeInfo.findAccessibilityNodeInfosByText(text);

            if (!nodes.isEmpty()) {
                if (text.equals(WECHAT_OPEN_EN) && !nodeInfo.findAccessibilityNodeInfosByText(WECHAT_OPENED_EN).isEmpty()) {
                    continue;
                }
                return nodes;
            }
        }
        return new ArrayList<>();
    }

    @Override
    public void onInterrupt() {

    }
}

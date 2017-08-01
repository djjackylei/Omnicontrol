package cn.diaovision.omnicontrol.core.message;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import cn.diaovision.omnicontrol.core.model.device.splicer.Scene;
import cn.diaovision.omnicontrol.util.ByteUtils;

/**
 * Created by TaoYimin on 2017/7/25.
 * 拼接控制器消息类
 */

public class SplicerMessage {
    //消息类型
    public final static int MSG_OPEN = 0;//新开窗口
    public final static int MSG_SHUT = 1;//关闭窗口
    public final static int MSG_MOVE = 2;//移动窗口命令
    public final static int MSG_SIZE = 3;//改变窗口大小命令
    public final static int MSG_MOVZ = 4;//层次关系命令
    public final static int MSG_SALL = 5;//关闭全部窗口
    public final static int MSG_OPE2 = 6;//输入信号源裁剪命令
    public final static int MSG_SWCH = 7;//信号源切换指令
    public final static int MSG_SAVE = 8;//保存场景
    public final static int MSG_CALL = 9;//调出场景
    public final static int MSG_RCNG = 10;//读取控制器多组屏状态
    public final static int MSG_RCN2 = 11;//读取多组屏的拼接参数和分辨率
    public final static int MSG_SCN2 = 12;//设置多组屏的拼接参数和分辨率
    public final static int MSG_RLST = 13;//读取场景列表
    public final static int MSG_SREN = 14;//重命名场景

    //消息类型对应数组
    public final static byte[][] TYPE_BYTES = {
            {'O', 'P', 'E', 'N'},//0
            {'S', 'H', 'U', 'T'},//1
            {'M', 'O', 'V', 'E'},//2
            {'S', 'I', 'Z', 'E'},//3
            {'M', 'O', 'V', 'Z'},//4
            {'S', 'A', 'L', 'L'},//5
            {'O', 'P', 'E', '2'},//6
            {'S', 'W', 'C', 'H'},//7
            {'S', 'A', 'V', 'E'},//8
            {'C', 'A', 'L', 'L'},//9
            {'R', 'C', 'N', 'G'},//10
            {'R', 'C', 'N', '2'},//11
            {'S', 'C', 'N', '2'},//12
            {'R', 'L', 'S', 'T'},//13
            {'S', 'R', 'E', 'N'},//14
    };

    private final byte header = '<';
    private final byte tail = '>';
    private byte[] type;
    private byte[] payload;

    public SplicerMessage(int type, byte[] payload) {
        this.type = TYPE_BYTES[type];
        this.payload = payload;
    }

    public byte[] toBytes() {
        //header+type+,+payload+tail
        byte[] bytes = new byte[1 + 4 + 1 + payload.length + 1];
        bytes[0] = header;
        bytes[1] = type[0];
        bytes[2] = type[1];
        bytes[3] = type[2];
        bytes[4] = type[3];
        bytes[5] = ',';
        System.arraycopy(payload, 0, bytes, 6, payload.length);
        bytes[bytes.length - 1] = tail;
        return bytes;
    }

    /*调出场景消息*/
    public static SplicerMessage buildLoadSceneMessage(int sceneId) {
        int len = (sceneId + "").toCharArray().length;
        byte[] payload = new byte[len];
        System.arraycopy(int2bytes(sceneId), 0, payload, 0, payload.length);
        return new SplicerMessage(MSG_CALL, payload);
    }

    /*读取控制器多组屏状态消息*/
    public static SplicerMessage buildReadScreenStateMessage() {
        byte[] payload = new byte[0];
        return new SplicerMessage(MSG_RCNG, payload);
    }

    /*读取多组屏的拼接参数和分辨率消息*/
    public static SplicerMessage buildReadParamPixelMessage(int groupId) {
        int len = (groupId + "").toCharArray().length;
        byte[] payload = new byte[len];
        System.arraycopy(int2bytes(groupId), 0, payload, 0, payload.length);
        return new SplicerMessage(MSG_RCN2, payload);
    }

    /*读取场景列表*/
    public static SplicerMessage buildReadSceneMessage(int groupId) {
        int len = (groupId + "").toCharArray().length;
        byte[] payload = new byte[len];
        System.arraycopy(int2bytes(groupId), 0, payload, 0, payload.length);
        return new SplicerMessage(MSG_RLST, payload);
    }

    /*重命名场景*/
    public static SplicerMessage buildRenameSceneMessage(int sceneId, String name, int groupId) {
        int sceneLen = (sceneId + "").toCharArray().length;
        int nameLen = URLEncoder.encode(name).toCharArray().length;
        int groupLen = (groupId + "").toCharArray().length;
        int len = sceneLen + 1 + nameLen + 1 + groupLen;
        byte[] payload = new byte[len];
        System.arraycopy(int2bytes(sceneId), 0, payload, 0, sceneLen);
        payload[sceneLen] = 44;
        System.arraycopy(name2bytes(name), 0, payload, sceneLen + 1, nameLen);
        payload[sceneLen + 1 + nameLen] = 44;
        System.arraycopy(int2bytes(groupId), 0, payload, sceneLen + 1 + nameLen + 1, groupId);
        return new SplicerMessage(MSG_SREN, payload);
    }

    /*融合器返回成功消息*/
    public static SplicerMessage buildSucessMessage(int type) {
        byte[] payload = new byte[]{'O', 'K'};
        return new SplicerMessage(type, payload);
    }

    private static byte[] int2bytes(int i) {
        char[] chars = (i + "").toCharArray();
        byte[] bytes = new byte[chars.length];
        for (int m = 0; m < chars.length; m++) {
            bytes[m] = (byte) chars[m];
        }
        return bytes;
    }

    private static byte[] name2bytes(String name) {
        char[] chars = java.net.URLEncoder.encode(name).toCharArray();
        byte[] bytes = new byte[chars.length];
        for (int m = 0; m < chars.length; m++) {
            bytes[m] = (byte) chars[m];
        }
        return bytes;
    }

    public static List<Scene> parseSceneMessage(List<byte[]> data) {
        List<Scene> sceneList = new ArrayList<>();
        if (data != null && data.size() != 0) {
            for (int i = data.size() - 2; i >= 0; i--) {
                byte[] bytes = data.get(i);
                String result = ByteUtils.bytes2ascii(bytes);
                String[] strs = result.substring(1, result.length() - 1).split(",");
                Scene scene = new Scene(Integer.parseInt(strs[2]), Integer.parseInt(strs[3]), Integer.parseInt(strs[4]), java.net.URLDecoder.decode(strs[5]));
                sceneList.add(scene);
            }
        }
        return sceneList;
    }


    public static SplicerMessage parseEndMessage(byte[] bytes) {
        int type = 0;
        byte[] types = new byte[]{bytes[1], bytes[2], bytes[3], bytes[4]};
        for (int i = 0; i < TYPE_BYTES.length; i++) {
            if (TYPE_BYTES[i] == types) {
                type = i;
            }
        }
        byte[] payload = new byte[]{'O', 'K'};
        return new SplicerMessage(type, payload);
    }

    public void setType(byte[] type) {
        this.type = type;
    }
}
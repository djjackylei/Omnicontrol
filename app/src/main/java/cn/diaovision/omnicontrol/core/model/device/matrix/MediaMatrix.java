package cn.diaovision.omnicontrol.core.model.device.matrix;

import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.diaovision.omnicontrol.conn.UdpClient;
import cn.diaovision.omnicontrol.core.message.MatrixMessage;
import cn.diaovision.omnicontrol.core.model.device.endpoint.HiCamera;
import cn.diaovision.omnicontrol.core.model.device.matrix.io.Channel;
import cn.diaovision.omnicontrol.core.model.device.matrix.io.Port;
import cn.diaovision.omnicontrol.core.model.medium.PreviewVideo;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by liulingfeng on 2017/2/21.
 */

//TODO: port, channel, check
public class MediaMatrix {

    int id;


    //ip control
    String ip;
    int port;

    //connection state
    boolean reachable = true;

    /*video resoures*/
    //Ports
    public List<Port> videoInPort = new ArrayList<>();
    public List<Port> videoOutPort = new ArrayList<>();

    //Channels
    public Set<Channel> videoChnSet = new HashSet<>();

    //Cameras
    public Map<Integer, HiCamera> cameras = new HashMap<>();

    //IP controller
    UdpClient controller;

    //preview channel
    PreviewVideo localPreviewVideo;
    //meeting channel
    PreviewVideo meetingPreviewVideo;

    /*other resources*/

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip, int port) {
        this.ip = ip;
        this.port = port;
        this.controller = new UdpClient(ip, port);
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public List<Port> getVideoInPort() {
        return videoInPort;
    }

    public void setVideoInPort(List<Port> videoInPort) {
        this.videoInPort = videoInPort;
    }

    public List<Port> getVideoOutPort() {
        return videoOutPort;
    }

    public void setVideoOutPort(List<Port> videoOutPort) {
        this.videoOutPort = videoOutPort;
    }

    public Set<Channel> getVideoChnSet() {
        return videoChnSet;
    }

    public void setVideoChnSet(Set<Channel> videoChnSet) {
        this.videoChnSet = videoChnSet;
    }

    public Map<Integer, HiCamera> getCameras() {
        return cameras;
    }

    public void setCameras(Map<Integer, HiCamera> cameras) {
        this.cameras = cameras;
    }

    public PreviewVideo getLocalPreviewVideo() {
        return localPreviewVideo;
    }

    public void setLocalPreviewVideo(PreviewVideo localPreviewVideo) {
        this.localPreviewVideo = localPreviewVideo;
    }

    public PreviewVideo getMeetingPreviewVideo() {
        return meetingPreviewVideo;
    }

    public void setMeetingPreviewVideo(PreviewVideo meetingPreviewVideo) {
        this.meetingPreviewVideo = meetingPreviewVideo;
    }

    public boolean isReachable() {
        return reachable;
    }

    public void setReachable(boolean reachable) {
        this.reachable = reachable;
    }

    public UdpClient getController() {
        if (controller == null){
            controller = new UdpClient(ip, port);
        }
        return controller;
    }

    //set port alias, return: porIdx if successful, -1 if failed
    public int setPortAlias(int portIdx, int dir, String alias){
        int res = -1;
        switch(dir){
            case Port.DIR_IN:
                if (portIdx > videoInPort.size() || portIdx < 0){
                    return res;
                }
                else {
                    res = portIdx;
                    videoInPort.get(portIdx).alias = alias;
                    return res;
                }
            case Port.DIR_OUT:
                if (portIdx > videoOutPort.size() || portIdx < 0){
                    return res;
                }
                else {
                    res = portIdx;
                    videoOutPort.get(portIdx).alias = alias;
                    return res;
                }
            default:
                return res;
        }
    }

    public int switchVideo(int in, int[] outs){
        //format check
        boolean formatGood = true;
        for (int out : outs){
            if (out >= videoOutPort.size() || out < 0){
                formatGood = false;
                break;
            }
        }

        if (in < videoInPort.size() && in >= 0 && formatGood){
            byte[] bytes;
            if (outs.length == 1) {
                bytes = MatrixMessage.buildSwitchMessage(id, in, outs[0]).toBytes();
            }
            else {
                bytes = MatrixMessage.buildMultiSwitchMessage(id, in, outs).toBytes();
            }
            byte[] recv = getController().send(bytes, bytes.length);
            if (recv.length > 0){
                updateChannel(in, outs, Channel.MOD_NORMAL);
                return 0; //switch successful
            }
            else {
                return -1; //failed to switch
            }
        }
        else {
            return -1;
        }
    }

    public int stitchVideo(int in, int[] outs, int colCnt, int rowCnt){
        //format check
        boolean formatGood = true;
        for (int out : outs){
            if (out >= videoOutPort.size() || out < 0){
                formatGood = false;
                break;
            }
        }

        if (in < videoInPort.size() && in > 0 && formatGood && colCnt > 0 && rowCnt > 0){
            byte[] bytes;
            bytes = MatrixMessage.buildMultiSwitchMessage(id, in, outs).toBytes();
            byte[] recv = getController().send(bytes, bytes.length);
            if (recv.length > 0){
                updateChannel(in, outs, Channel.MOD_NORMAL);

                bytes = MatrixMessage.buildStitchMessage(id, colCnt, rowCnt, outs).toBytes();
                recv = getController().send(bytes, bytes.length);

                if (recv.length > 0){
                    updateChannel(in, outs, Channel.MOD_STITCH);
                    return in;
                }
                else {
                    return -1; //failed to switch
                }
            }
            else {
                return -1; //failed to switch
            }
        }
        else {
            return -1;
        }
    }

    public int getCameraIdx(int portIdx){
        byte[] bytes = MatrixMessage.buildGetCameraInfoMessage(id, portIdx).toBytes();
        byte[] recv = getController().send(bytes, bytes.length);
        if (recv.length > 0){
            int idx = 0;
            if (recv[5] >= '9'){
                idx += (recv[5] - 0x30-7)<<4;
            }
            else {
                idx += (recv[5] - 0x30)<<4;
            }

            if (recv[6] >= '9'){
                idx += (recv[6] - 0x30-7);
            }
            else {
                idx += (recv[6] - 0x30);
            }

            return idx;
        }
        else {
            return -1;
        }
    }

    public void addCamera(int portIdx, int camIdx, int baudrate, int proto){
        cameras.put(portIdx, new HiCamera(portIdx, camIdx, baudrate, proto));
    }

    public int startCameraGo(int portIdx, int cmd, int speed){
        HiCamera cam = cameras.get(portIdx);
        if (cam == null){
            return -1;
        }
        final byte[] bytes = MatrixMessage.buildStartCameraGoMessage(id, cam.getBaudrate(), cam.getProto(), cam.getPortIdx(), cmd, speed).toBytes();
        byte[] recv = getController().send(bytes, bytes.length);
        if (recv.length > 0){
            return 0;
        }
        else {
            return -1;
        }
    }

    public int stopCameraGo(int portIdx){
        HiCamera cam = cameras.get(portIdx);
        if (cam == null){
            return -1;
        }
        byte[] bytes = MatrixMessage.buildStopCameraGoMessage(id, cam.getBaudrate(), cam.getProto(), cam.getPortIdx()).toBytes();
        byte[] recv = getController().send(bytes, bytes.length);
        if (recv.length > 0){
            return recv.length;
        }
        else {
            return -1;
        }
    }

    public int setCameraPreset(int portIdx, int presetIdx, String name){
        HiCamera cam = cameras.get(portIdx);
        if (cam == null){
            return -1;
        }

        byte[] bytes = MatrixMessage.buildSetCameraPresetMessgae(id, cam.getBaudrate(), cam.getProto(), portIdx, presetIdx).toBytes();
        byte[] recv = getController().send(bytes, bytes.length);
        if (recv.length > 0){
            //Clear previous preset
            boolean containPreset = false;
            List<HiCamera.Preset> presetList = cam.getPresetList();
            for (HiCamera.Preset preset : presetList){
                if(preset.getIdx() == presetIdx){
                    containPreset = true;
                    preset.setName(name);
                    break;
                }
            }
            if (!containPreset) {
                cam.getPresetList().add(new HiCamera.Preset(name, presetIdx));
            }
            return 0;
        }
        else {
            return -1;
        }
    }

    public int loadCameraPreset(int portIdx, int presetIdx){
        HiCamera cam = cameras.get(portIdx);
        if (cam == null){
            return -1;
        }

        HiCamera.Preset preset = null;
        for (HiCamera.Preset p : cam.getPresetList()){
            if (p.getIdx() == presetIdx){
                preset = p;
                break;
            }
        }

        if (preset == null){
            return -1;
        }

        byte[] bytes = MatrixMessage.buildLoadCameraPresetMessgae(id, cam.getBaudrate(), cam.getProto(), portIdx, presetIdx).toBytes();
        byte[] recv = getController().send(bytes, bytes.length);
        if (recv.length > 0){
            return recv.length;
        }
        else {
            return -1;
        }
    }

    public int clearCameraPreset(int portIdx, int presetIdx, String name){
        HiCamera cam = cameras.get(portIdx);
        if (cam == null){
            return -1;
        }

        HiCamera.Preset preset = null;
        for (HiCamera.Preset p : cam.getPresetList()){
            if (p.getIdx() == presetIdx){
                preset = p;
                break;
            }
        }

        if (preset == null){
            return -1;
        }

        byte[] bytes = MatrixMessage.buildClearCameraPresetMessgae(id, cam.getBaudrate(), cam.getProto(), portIdx, presetIdx).toBytes();
        byte[] recv = getController().send(bytes, bytes.length);
        if (recv.length > 0){
            cam.getPresetList().add(new HiCamera.Preset(name, presetIdx));
            return recv.length;
        }
        else {
            return -1;
        }
    }
    //update channel in two cases: switch or stitch
    public void updateChannel(int in, int[] outs, int mode){
        //clear channels with outs or in
        for (Channel ch : videoChnSet){
            boolean containOut = false;
            int[] chOuts = ch.outputIdx;
            for (int m : chOuts){
                for (int n : outs){
                    if (m == n){
                        containOut = true;
                        break;
                    }
                }
            }

            if (ch.inputIdx == in || containOut){
                videoChnSet.remove(ch);
            }
        }

        //add new channel and set them as  mode
        Channel ch = new Channel(Channel.CHN_VIDEO, in, outs);
        ch.mode = mode;
        videoChnSet.add(ch);
    }


    public static class Builder{
        int id = 0;
        String ip = "192.168.1.1";
        int port = 5000;
        String localPreviewIp = "192.168.1.2";
        int localPreviewPort = 553;
        String meetingPreviewIp = "192.168.10.1";
        int meetingPreviewPort = 553;
        List<Port> videoInPort = new ArrayList<>();
        List<Port> videoOutPort = new ArrayList<>();

        Set<Channel> vChannelSet = new HashSet<>();

        public Builder id(int id){
            this.id = id;
            return this;
        }

        public Builder ip(String ip){
            this.ip = ip;
            return this;
        }

        public Builder port(int port){
            this.port = port;
            return this;
        }

        public Builder localPreviewVideo(String str, int port){
            localPreviewIp = str;
            localPreviewPort = port;
            return this;
        }

        public Builder meetingPreviewVideo(String str, int port){
            meetingPreviewIp = str;
            meetingPreviewPort = port;
            return this;
        }

        public Builder videoInInit(int num){
            for (int m = 0; m < num; m ++){
                videoInPort.add(new Port(id, num, Port.TYPE_VIDEO, Port.DIR_IN));
            }
            return this;
        }

        public Builder videoOutInit(int num){
            for (int m = 0; m < num; m ++){
                videoOutPort.add(new Port(id, num, Port.TYPE_VIDEO, Port.DIR_OUT));
            }
            return this;
        }

        public Builder videoChn(int inIdx, int outIdx){
            int[] outs = new int[1];
            outs[0] = outIdx;
            Channel chn = new Channel(Channel.CHN_VIDEO, inIdx, outs);
            chn.mode = Channel.MOD_NORMAL;
            vChannelSet.add(chn);
            return this;
        }


        public MediaMatrix build(){
            MediaMatrix mm = new MediaMatrix();
            mm.setId(id);
            mm.setIp(ip, port);
            mm.setVideoInPort(videoInPort);
            mm.setVideoOutPort(videoOutPort);
            mm.setVideoChnSet(vChannelSet);
            mm.setMeetingPreviewVideo(new PreviewVideo(meetingPreviewIp, meetingPreviewPort));
            mm.setLocalPreviewVideo(new PreviewVideo(meetingPreviewIp, meetingPreviewPort));

            return mm;
        }
    }
}
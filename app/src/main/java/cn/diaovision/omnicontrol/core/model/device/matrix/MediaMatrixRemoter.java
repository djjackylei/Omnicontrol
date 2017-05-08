package cn.diaovision.omnicontrol.core.model.device.matrix;

import org.reactivestreams.Publisher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cn.diaovision.omnicontrol.core.message.MatrixMessage;
import cn.diaovision.omnicontrol.core.model.device.endpoint.HiCamera;
import cn.diaovision.omnicontrol.core.model.device.matrix.io.Channel;
import cn.diaovision.omnicontrol.model.Config;
import cn.diaovision.omnicontrol.model.ConfigFixed;
import cn.diaovision.omnicontrol.rx.RxExecutor;
import cn.diaovision.omnicontrol.rx.RxMessage;
import cn.diaovision.omnicontrol.rx.RxReq;
import cn.diaovision.omnicontrol.rx.RxSubscriber;
import io.reactivex.BackpressureStrategy;
import io.reactivex.CompletableSource;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Action;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

/**矩阵控制器，这里用remoter，以区分controller
 * Created by liulingfeng on 2017/5/5.
 */

public class MediaMatrixRemoter {
    int a;
    private MediaMatrix matrix;

    public MediaMatrixRemoter(MediaMatrix matrix) {
        this.matrix = matrix;
    }

    public MediaMatrix getMatrix() {
        return matrix;
    }

    public void setMatrix(MediaMatrix matrix) {
        this.matrix = matrix;
    }

    public int switchVideo(final int portIn, final int[] portOut, RxSubscriber<RxMessage> subscriber){
        if (matrix == null || !matrix.isReachable())
            return -1;

        Flowable.create(new FlowableOnSubscribe<RxMessage>() {
            @Override
            public void subscribe(FlowableEmitter<RxMessage> e) throws Exception {
                byte[] bytes = MatrixMessage.buildMultiSwitchMessage(matrix.id, portIn, portOut).toBytes();
                byte[] recv = matrix.getController().send(bytes, bytes.length);
                if (recv.length > 0) {
                    e.onNext(new RxMessage(RxMessage.DONE));
                    e.onComplete();
                }
                else {
                    matrix.setReachable(false);
                    e.onError(new IOException());
                }
            }
        }, BackpressureStrategy.BUFFER)
        .doOnComplete(new Action() {
            @Override
            public void run() throws Exception {
                matrix.updateChannel(portIn, portOut, Channel.MOD_NORMAL);
            }
        })
        .subscribe(subscriber);
        return 0;
    }

    public int stitchVideo(final int portIn, final int columnCnt, final int rowCnt, final int[] portOut, RxSubscriber<RxMessage> subscriber){
        if (matrix == null || !matrix.isReachable())
            return -1;

        //send multi -> send stitch
        MatrixMessage multiSwithMsg = MatrixMessage.buildMultiSwitchMessage(matrix.id, portIn, portOut);
        MatrixMessage stitchMsg = MatrixMessage.buildStitchMessage(matrix.id, columnCnt, rowCnt, portOut);
        List<MatrixMessage> msgList = new ArrayList<>();
        msgList.add(multiSwithMsg);
        msgList.add(stitchMsg);

        Flowable.fromIterable(msgList)
                .map(new Function<MatrixMessage, RxMessage>() {
                    @Override
                    public RxMessage apply(MatrixMessage matrixMessage) throws Exception {
                        byte[] recv = matrix.getController().send(matrixMessage.toBytes(), matrixMessage.toBytes().length);
                        if (recv.length > 0) {
                            //4. send message
                            return new RxMessage(RxMessage.DONE);
                        }
                        else {
                            matrix.setReachable(false);
                            throw new IOException();
                        }
                    }
                })
                .doOnComplete(new Action() {
                    @Override
                    public void run() throws Exception {
                        matrix.updateChannel(portIn, portOut, Channel.MOD_STITCH);
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(subscriber);
        return 0;
    }

    public int getCameraIdx(final int portIdx, RxSubscriber<RxMessage> subscriber){
        if (matrix == null || !matrix.isReachable()) {
            return -1;
        }

        Flowable.just(portIdx)
                .map(new Function<Integer, RxMessage>() {
                    @Override
                    public RxMessage apply(Integer integer) throws Exception {
                        byte[] bytes = MatrixMessage.buildGetCameraInfoMessage(matrix.id, portIdx).toBytes();
                        byte[] recv = matrix.getController().send(bytes, bytes.length);
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
                            return new RxMessage(RxMessage.DONE, idx);
                        }
                        else {
                            throw new IOException();
                        }
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(subscriber);
        return 0;
    }

    public int startCameraGo(final int portIdx, final int cmd, final int speed, RxSubscriber<RxMessage> subscriber){
        if (matrix == null || !matrix.isReachable()) {
            return -1;
        }
        final HiCamera cam = matrix.getCameras().get(portIdx);
        if (cam == null){
            return -1;
        }

        Flowable.create(new FlowableOnSubscribe<RxMessage>() {
            @Override
            public void subscribe(FlowableEmitter<RxMessage> e) throws Exception {

                final byte[] bytes = MatrixMessage.buildStartCameraGoMessage(matrix.id, cam.getBaudrate(), cam.getProto(), cam.getPortIdx(), cmd, speed).toBytes();
                byte[] recv = matrix.getController().send(bytes, bytes.length);
                if (recv.length > 0){
                    e.onComplete();
                }
                else {
                    matrix.setReachable(false);
                    e.onError(new IOException());
                }
            }
        }, BackpressureStrategy.BUFFER)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(subscriber);
        return 0;
    }

    public int stopCameraGo(final int portIdx, RxSubscriber<RxMessage> subscriber){
        if (matrix == null || !matrix.isReachable()) {
            return -1;
        }
        final HiCamera cam = matrix.getCameras().get(portIdx);
        if (cam == null){
            return -1;
        }

        Flowable.create(new FlowableOnSubscribe<RxMessage>() {
            @Override
            public void subscribe(FlowableEmitter<RxMessage> e) throws Exception {

                byte[] bytes = MatrixMessage.buildStopCameraGoMessage(matrix.id, cam.getBaudrate(), cam.getProto(), cam.getPortIdx()).toBytes();
                byte[] recv = matrix.getController().send(bytes, bytes.length);
                if (recv.length > 0){
                    e.onComplete();
                }
                else {
                    matrix.setReachable(false);
                    e.onError(new IOException());
                }
            }
        }, BackpressureStrategy.BUFFER)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(subscriber);
        return 0;
    }

    public int storeCameraPreset(final int portIdx, final int presetIdx, final String name, RxSubscriber subscriber){
        if (matrix == null || !matrix.isReachable()) {
            return -1;
        }
        final HiCamera cam = matrix.getCameras().get(portIdx);
        if (cam == null){
            return -1;
        }

        if (matrix == null)
            return -1;

        Flowable.create(new FlowableOnSubscribe<RxMessage>() {
            @Override
            public void subscribe(FlowableEmitter<RxMessage> e) throws Exception {
                byte[] bytes = MatrixMessage.buildSetCameraPresetMessgae(matrix.id, cam.getBaudrate(), cam.getProto(), portIdx, presetIdx).toBytes();
                byte[] recv = matrix.getController().send(bytes, bytes.length);

                if (recv.length > 0) {
                    e.onNext(new RxMessage(RxMessage.DONE));
                    e.onComplete();
                }
                else {
                    matrix.setReachable(false);
                    e.onError(new IOException());
                }
            }
        }, BackpressureStrategy.BUFFER)
        .doOnComplete(new Action() {
            @Override
            public void run() throws Exception {
                HiCamera.Preset preset = new HiCamera.Preset(name, presetIdx);
                cam.updatePreset(preset);
            }
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(subscriber);

        return 0;
    }

    public int loadCameraPreset(final int portIdx, final int presetIdx, RxSubscriber subscriber){
        if (matrix == null || !matrix.isReachable()) {
            return -1;
        }
        final HiCamera cam = matrix.getCameras().get(portIdx);
        if (cam == null){
            return -1;
        }

        Flowable.create(new FlowableOnSubscribe<RxMessage>() {
            @Override
            public void subscribe(FlowableEmitter<RxMessage> e) throws Exception {
                byte[] bytes = MatrixMessage.buildLoadCameraPresetMessgae(matrix.id, cam.getBaudrate(), cam.getProto(), portIdx, presetIdx).toBytes();
                byte[] recv = matrix.getController().send(bytes, bytes.length);

                if (recv.length > 0) {
                    e.onNext(new RxMessage(RxMessage.DONE));
                    e.onComplete();
                }
                else {
                    matrix.setReachable(false);
                    e.onError(new IOException());
                }
            }
        }, BackpressureStrategy.BUFFER)
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(subscriber);

        return 0;
    }

    public int removeCameraPreset(final int portIdx, final int presetIdx, RxSubscriber subscriber){
        if (matrix == null || !matrix.isReachable()) {
            return -1;
        }

        final HiCamera cam = matrix.getCameras().get(portIdx);
        if (cam == null || cam.getPreset(presetIdx) >= 0){
            return -1;
        }

        Flowable.create(new FlowableOnSubscribe<RxMessage>() {
            @Override
            public void subscribe(FlowableEmitter<RxMessage> e) throws Exception {

                byte[] bytes = MatrixMessage.buildClearCameraPresetMessgae(matrix.id, cam.getBaudrate(), cam.getProto(), portIdx, presetIdx).toBytes();
                byte[] recv = matrix.getController().send(bytes, bytes.length);

                if (recv.length > 0) {
                    e.onNext(new RxMessage(RxMessage.DONE));
                    e.onComplete();
                }
                else {
                    matrix.setReachable(false);
                    e.onError(new IOException());
                }
            }
        }, BackpressureStrategy.BUFFER)
        .doOnComplete(new Action() {
            @Override
            public void run() throws Exception {
                cam.deletePreset(cam.getPreset(presetIdx));
            }
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(subscriber);

        return 0;
    }
}

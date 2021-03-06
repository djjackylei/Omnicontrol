package cn.diaovision.omnicontrol.view;

import java.util.List;
import java.util.Set;

import cn.diaovision.omnicontrol.BasePresenter;
import cn.diaovision.omnicontrol.BaseView;
import cn.diaovision.omnicontrol.core.model.device.matrix.io.Channel;
import cn.diaovision.omnicontrol.core.model.device.matrix.io.Port;

/* *
 * view + presenter统一的接口
 * 如果fragment不存在presenter和view的替换，可以转换为实体类
 * 默认单独定义presenter
 * Created by liulingfeng on 2017/4/3.
 * */

public interface VideoContract {
    interface View extends BaseView<Presenter>{
        //void initScene(List<Scene> list);
        void showToast(String string);
        //void refreshSceneList();
    }

    interface Presenter extends BasePresenter{
        int[] getOutputIdx(int inputIdx);
        int getInputIdx(int outputIdx);
        List<Port> getInputList();
        List<Port> getOutputList();
        Set<Channel> getChannelSet();
        //void setChannel(int input, int[] outputs,int mode);
        void switchVideo(int portIn, int[] portOut);
        void stitchVideo(int portIn,  int columnCnt,  int rowCnt,  int[] portOut);
        void switchPreviewVideo(int portIn, int portOut);
        void setSubtitle(int portIdx, String str);
        //void getSceneList(int group);
        //void loadSceneList(int sceneId);
        //void renameScene(int sceneId,String name,int groupId);

/*        interface PortStateListener{
            void onPreviewDisconnected();
            void onPreviewConnected();
            void onMatrixDisconnected();
        }*/
    }
}

package cn.diaovision.omnicontrol.view;

import android.app.Service;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.support.annotation.Nullable;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import cn.diaovision.omnicontrol.BaseFragment;
import cn.diaovision.omnicontrol.MainControlActivity;
import cn.diaovision.omnicontrol.R;
import cn.diaovision.omnicontrol.core.model.device.endpoint.HiCamera;
import cn.diaovision.omnicontrol.core.model.device.matrix.io.Port;
import cn.diaovision.omnicontrol.widget.AssistDrawerLayout;
import cn.diaovision.omnicontrol.widget.ItemSelectionSupport;
import cn.diaovision.omnicontrol.widget.OnRecyclerItemClickListener;
import cn.diaovision.omnicontrol.widget.PortDialog;
import cn.diaovision.omnicontrol.widget.VideoLayout;
import cn.diaovision.omnicontrol.widget.adapter.PortAdapter;

/**
 * Created by TaoYimin on 2017/5/18.
 */

public class VideoFragment extends BaseFragment implements VideoContract.View {
    @BindView(R.id.input)
    RecyclerView inputRecyclerView;
    @BindView(R.id.output)
    RecyclerView outputRecyclerView;
    @BindView(R.id.assist_drawer_layout)
    AssistDrawerLayout drawerLayout;
    @BindView(R.id.edit_subtitle)
    EditText editSubtitle;
    @BindView(R.id.video_layout)
    VideoLayout ijkVideoView;
    @BindView(R.id.auxiliary_recycler)
    RecyclerView auxiliaryRecycler;

    private PortAdapter inputAdapter;
    private PortAdapter outputAdapter;
    private ItemSelectionSupport inputSelectionSupport;
    private ItemSelectionSupport outputSelectionSupport;
    private Rect rect = new Rect();
    private static final int OPEN_DRAWER = 0;
    private static final int CLOSE_DRAWER = 1;
    VideoContract.Presenter presenter;

    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case OPEN_DRAWER:
                    drawerLayout.openDrawer();
                    break;
                case CLOSE_DRAWER:
                    drawerLayout.closeDrawer();
                    break;
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_video, container, false);
        ButterKnife.bind(this, v);
        return v;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        //初始化输入输出端口列表
        inputSelectionSupport = new ItemSelectionSupport(inputRecyclerView);
        outputSelectionSupport = new ItemSelectionSupport(outputRecyclerView);
        inputSelectionSupport.setChoiceMode(ItemSelectionSupport.ChoiceMode.SINGLE);
        outputSelectionSupport.setChoiceMode(ItemSelectionSupport.ChoiceMode.SINGLE);
        inputAdapter = new PortAdapter(presenter.getInputList(), inputSelectionSupport);
        outputAdapter = new PortAdapter(presenter.getOutputList(), outputSelectionSupport);
        inputRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 6));
        outputRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 6));
        inputRecyclerView.setHasFixedSize(true);
        outputRecyclerView.setHasFixedSize(true);
        inputRecyclerView.setAdapter(inputAdapter);
        outputRecyclerView.setAdapter(outputAdapter);
        //监听标题编辑框的焦点状态
        editSubtitle.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    int portIdx = inputSelectionSupport.getCheckedItemPosition();
                    if (portIdx == -1) {
                        Toast.makeText(getContext(), "请先选择需要设置标题的输入端！", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    presenter.setSubtitle(portIdx, editSubtitle.getText().toString());
                }
            }
        });
        //左上角抽屉布局的监听
        drawerLayout.setOnEditCompleteListener(new AssistDrawerLayout.OnEditCompleteListener() {
            @Override
            public void onComplete(int mode) {
                //当前选中输入端
                int in = inputSelectionSupport.getCheckedItemPosition();
                //当前选中输出端
                List<Integer> selects = outputSelectionSupport.getCheckedItemPositions();
                int[] outs = new int[selects.size()];
                for (int i = 0; i < selects.size(); i++) {
                    outs[i] = selects.get(i);
                }
                switch (mode) {
                    case AssistDrawerLayout.MODE_1XN:
                        presenter.switchVideo(in, outs);
                        break;
                    case AssistDrawerLayout.MODE_2X1:
                        presenter.stitchVideo(in, 2, 1, outs);
                        break;
                    case AssistDrawerLayout.MODE_2X2:
                        presenter.stitchVideo(in, 2, 2, outs);
                        break;
                    case AssistDrawerLayout.MODE_3X3:
                        presenter.stitchVideo(in, 3, 3, outs);
                        break;
                }
                editComplete();
            }
        });

        inputRecyclerView.addOnItemTouchListener(new OnRecyclerItemClickListener(inputRecyclerView) {
            @Override
            public void onItemClick(RecyclerView.ViewHolder vh, int position) {
                inputSelectionSupport.itemClick(position);
                //初始化输出端选择的颜色和角标
                outputSelectionSupport.initChoiceConfig(inputAdapter.getData().get(position));
            }

            @Override
            public void onLongClick(RecyclerView.ViewHolder vh, final int position) {
                if (inputSelectionSupport.getChoiceMode() == ItemSelectionSupport.ChoiceMode.SINGLE && outputSelectionSupport.getChoiceMode() == ItemSelectionSupport.ChoiceMode.SINGLE) {
                    //获取系统震动服务
                    Vibrator vib = (Vibrator) getActivity().getSystemService(Service.VIBRATOR_SERVICE);
                    //震动70毫秒
                    vib.vibrate(70);
                    //输入端输出端都为单选模式
                    //初始化输出端选择的颜色和角标
                    outputSelectionSupport.initChoiceConfig(inputAdapter.getData().get(position));
                    if (inputSelectionSupport.isItemChecked(position)) {
                        //长按的item已经被选中
                        outputSelectionSupport.setChoiceMode(ItemSelectionSupport.ChoiceMode.MULTIPLE);
                        outputAdapter.notifyDataSetChanged();
                    } else {
                        //长按的item还未被选中
                        outputSelectionSupport.itemLongClick(-1);
                        inputSelectionSupport.itemClick(position);
                        inputAdapter.notifyDataSetChanged();
                    }
                    //弹出抽屉，直接调用drawerLayout.openDrawer()方法没有弹出效果
                    handler.sendEmptyMessage(0);
                } else if (inputSelectionSupport.getChoiceMode() == ItemSelectionSupport.ChoiceMode.MULTIPLE) {
                    //输入端为多选模式，输出端为单选模式
                    inputSelectionSupport.itemLongClick(position);
                } else {
                    //输入端为单选模式，输出端为多选模式
                    inputSelectionSupport.itemClick(position);
                }
            }

            @Override
            public void onItemDoubleClick(RecyclerView.ViewHolder vh, int position) {
                popupDialog(presenter.getInputList().get(position), position);
            }
        });

        outputRecyclerView.addOnItemTouchListener(new OnRecyclerItemClickListener(outputRecyclerView) {
            @Override
            public void onItemClick(RecyclerView.ViewHolder vh, int position) {
                outputSelectionSupport.itemClick(position);
            }

            @Override
            public void onItemDoubleClick(RecyclerView.ViewHolder vh, int position) {
                popupDialog(presenter.getOutputList().get(position), position);
            }
        });

        inputSelectionSupport.setOnItemStatueListener(new ItemSelectionSupport.OnItemStatueListener() {
            @Override
            public void onSelectSingle(int position) {
                //获取到选中输入端对应的输出端
                int[] outsIdx = presenter.getOutputIdx(presenter.getInputList().get(position).idx);
                outputSelectionSupport.clearChoices();
                if (outsIdx != null) {
                    for (int outIdx : outsIdx) {
                        //将对应的输出端设为选中状态
                        outputSelectionSupport.setItemChecked(outIdx, true);
                    }
                }
                outputAdapter.notifyDataSetChanged();
                presenter.switchPreviewVideo(position, MainControlActivity.cfg.getMatrixPreviewPort());
                ijkVideoView.setVideoPath("rtsp://" + MainControlActivity.cfg.getMatrixPreviewIp() + "/test1.ts");
                ijkVideoView.start();
                editSubtitle.setText("");
            }

            @Override
            public void onUnSelectSingle(int position) {
                //清空输出端列表的所有选中状态
                outputSelectionSupport.clearChoices();
                outputAdapter.notifyDataSetChanged();
                ijkVideoView.stopPlayback();
                editSubtitle.setText("");
            }

            @Override
            public void onSelectMultiple(int position) {

            }

            @Override
            public void onUnSelectMultiple(int position) {

            }
        });

        outputSelectionSupport.setOnItemStatueListener(new ItemSelectionSupport.OnItemStatueListener() {
            @Override
            public void onSelectSingle(int position) {
                //获取选中输出端对应的输入端
                int inIdx = presenter.getInputIdx(presenter.getOutputList().get(position).idx);
                inputSelectionSupport.clearChoices();
                //将对应的输入端设为选中状态
                if (inIdx != -1) {
                    inputSelectionSupport.setItemChecked(inIdx, true);
                    inputAdapter.notifyDataSetChanged();
                    //获取对应输入端所对应的所有输出端
                    int[] outsIdx = presenter.getOutputIdx(inIdx);
                    outputSelectionSupport.clearChoices();
                    //将对应的输出端设为选中状态
                    if (outsIdx != null) {
                        for (int outIdx : outsIdx) {
                            outputSelectionSupport.setItemChecked(outIdx, true);
                        }
                    }
                    outputAdapter.notifyDataSetChanged();
                } else {
                    inputAdapter.notifyDataSetChanged();
                }
                editSubtitle.setText("");
            }

            @Override
            public void onUnSelectSingle(int position) {
                //清空输入端列表所有选中状态
                inputSelectionSupport.clearChoices();
                inputAdapter.notifyDataSetChanged();
                editSubtitle.setText("");
            }

            @Override
            public void onSelectMultiple(int position) {

            }

            @Override
            public void onUnSelectMultiple(int position) {

            }
        });

        //presenter.getSceneList(1);
    }

    public void popupDialog(final Port port, final int position) {
        final PortDialog dialog = new PortDialog(getContext(), port);
        final int preCategory = port.category;
        dialog.show();
        dialog.setOnButtonClickListener(new PortDialog.OnButtonClickListener() {
            @Override
            public void onConfirmClick() {
                //对话框的确定按钮按下，回调到这里
                dialog.dismiss();
                if (port.dir == Port.DIR_IN) {
                    inputAdapter.notifyItemChanged(position);
                } else if (port.dir == Port.DIR_OUT) {
                    outputAdapter.notifyItemChanged(position);
                } else {
                    inputAdapter.notifyDataSetChanged();
                    outputAdapter.notifyDataSetChanged();
                }
                if (preCategory == Port.CATEGORY_CAMERA && port.category != Port.CATEGORY_CAMERA) {
                    //将摄像机改为其它类型的情况，删除该摄像机的配置信息
                    MainControlActivity.cfg.deleteCamera(MainControlActivity.cfg.getMatrixCameras().get(port.idx));
                    MainControlActivity.matrix.deleteCamera(port.idx);
                }
                //存储到配置文件
                MainControlActivity.cfg.setPort(port);
                if (port.category == Port.CATEGORY_IP) {
                    //如果是预览卡，则把端口号写入配置文件中
                    MainControlActivity.cfg.setPreviewVideoPort(port.idx);
                } else if (port.category == Port.CATEGORY_CAMERA) {
                    //如果是摄像机，则把摄像机信息写入配置文件中
                    HiCamera camera = new HiCamera(port.idx, 0, 1200, 0);
                    camera.setAlias(port.alias);
                    MainControlActivity.cfg.setCamera(camera);
                    MainControlActivity.matrix.addCamera(camera);
                }
            }
        });
    }

    public void getActivityDispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            if (editSubtitle == null)
                return;
            editSubtitle.getGlobalVisibleRect(rect);
            boolean flag = rect.contains((int) event.getRawX(), (int) event.getRawY());
            if (!flag) {
                editSubtitle.clearFocus();
            }
        }
    }

    @Override
    public void bindPresenter() {
        presenter = new VideoPresenter(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        //停止视频播放，并释放资源
        ijkVideoView.stopPlayback();
        //IjkMediaPlayer.native_profileEnd();
    }

    public AssistDrawerLayout getDrawerLayout() {
        return drawerLayout;
    }

    /*设置完端口切换模式后调用*/
    public void editComplete(){
        //编辑完成后设为单选模式
        outputSelectionSupport.setChoiceMode(ItemSelectionSupport.ChoiceMode.SINGLE);
        outputAdapter.notifyDataSetChanged();
        inputAdapter.notifyDataSetChanged();
        //还原输出端选择的颜色和角标
        outputSelectionSupport.initChoiceConfig(null);
        //关闭抽屉，直接调用drawerLayout.closeDrawer()方法没有收回效果
        handler.sendEmptyMessage(CLOSE_DRAWER);
    }

    /*弹出吐司*/
    @Override
    public void showToast(String string) {
        Toast.makeText(getContext(),string,Toast.LENGTH_SHORT).show();
    }
}

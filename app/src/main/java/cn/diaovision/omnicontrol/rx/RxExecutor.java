package cn.diaovision.omnicontrol.rx;

import org.reactivestreams.Subscriber;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

/**
 * 统一的Rx异步调用接口
 * Created by liulingfeng on 2017/4/3.
 */

public class RxExecutor {
    public final static int SCH_IO = 1;
    public final static int SCH_COMPUTATION = 2;
    public final static int SCH_NEW = 3;
    public final static int SCH_ANDROID_MAIN = 4;

    static private RxExecutor instance;

    private RxExecutor(){
    }

    /*单例模式*/
    static public RxExecutor getInstance(){
        if (instance == null){
            instance = new RxExecutor();
        }
        return instance;
    }

    /*a simple rx call with consumer*/
    public void post(final RxReq req, Consumer consumer, int subsribeOn, int observeOn){
        Flowable.just("")
                .map(new Function<Object, Object>() {
                    @Override
                    public Object apply(Object o) throws Exception {
                        RxMessage res = req.request();
                        return res;
                    }
                })
                .subscribeOn(getScheduler(subsribeOn))
                .observeOn(getScheduler(observeOn))
                .subscribe(consumer);
    }


    /*a standard rx call: with subscriber*/
    public void post(final RxReq req, Subscriber subscriber, int subsribeOn, int observeOn){
        Flowable.create(new FlowableOnSubscribe<Object>() {
            @Override
            public void subscribe(FlowableEmitter<Object> e) throws Exception {
                RxMessage res = req.request();
                if (res == null){
                    e.onError(new RuntimeException());
                }
                e.onNext(res);
            }
        }, BackpressureStrategy.BUFFER)
                .subscribeOn(getScheduler(subsribeOn))
                .observeOn(getScheduler(observeOn));
    }

    private Scheduler getScheduler(int type){
        switch (type){
            case SCH_IO:
                return Schedulers.io();
            case SCH_COMPUTATION:
                return Schedulers.computation();
            case SCH_NEW:
                return Schedulers.newThread();
            case SCH_ANDROID_MAIN:
                return AndroidSchedulers.mainThread();
            default:
                return Schedulers.newThread();
        }
    }
}

/*
**
** Copyright 2013, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/


package com.android.commands.samplewindow;

import android.graphics.LinearGradient;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Debug;
import android.os.UserHandle;
import android.util.Log;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Binder;
import android.view.Choreographer;
import android.view.Display;
import android.view.Gravity;
import android.view.IWindowSession;
import android.view.InputChannel;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.IBinder;
import android.graphics.Point;
import android.graphics.Paint;
import android.content.Context;
import android.os.Looper;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.DragEvent;
import android.view.WindowManager;
import android.view.InputEventReceiver;
import android.view.IWindowManager;
import android.os.ServiceManager;
import android.view.WindowManagerGlobal;
import android.hardware.display.IDisplayManager;
import android.view.DisplayInfo;
import android.view.IWindow;
import android.view.WindowManager.LayoutParams;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import com.android.internal.os.IResultReceiver;
import android.util.MergedConfiguration;
import android.view.DisplayCutout;


public class SampleWindow {

    // IWindowSession 是客户端向WMS请求窗口操作的中间代理，并且是进程唯一的
    IWindowSession mSession ;
    // InputChannel 是窗口接收用户输入事件的管道。在第5章中将对其进行详细探讨
    InputChannel mInputChannel = new InputChannel();
    // 下面的三个Rect保存了窗口的布局结果。其中mFrame表示了窗口在屏幕上的位置与尺寸
    // 在4.4节中将详细介绍它们的作用以及计算原理
    // final Rect mInsets = new Rect();
    // final Rect mFrame = new Rect();
    // Rect mVisibleInsets = new Rect();
    final Rect mVisibleInsets = new Rect();
    final Rect mWinFrame = new Rect();
    final Rect mOverscanInsets = new Rect();
    final Rect mContentInsets = new Rect();
    final Rect mStableInsets = new Rect();
    final Rect mOutsets = new Rect();
    final Rect mBackdropFrame = new Rect();
    final Rect mTmpRect = new Rect();
    MergedConfiguration mConfig = new MergedConfiguration();
    // 窗口的Surface，在此Surface上进行的绘制都将在此窗口上显示出来
    Surface mSurface = new Surface();
    // 用于在窗口上进行绘图的画刷
    Paint mPaint = new Paint();
    // 添加窗口所需的令牌，在4.2节将会对其进行介绍
    IBinder mToken = new Binder();
    // 一个窗口对象，本例演示了如何将此窗口添加到WMS中，并在其上进行绘制操作
    MyWindow mWindow = new MyWindow();
    LayoutParams mLp = new LayoutParams();
    Choreographer mChoreographer = null;
    // InputHandler 用于从InputChannel接收按键事件并做出响应
    InputHandler mInputHandler = null;
    boolean mContinueAnime = true;

    public static void main(String[] args) {
        try{
           SampleWindow sampleWindow = new SampleWindow();
           sampleWindow.run();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void run() throws Exception{
    	Log.i("dengsam","Run...111");
        //android.ddm.DdmHandleAppName.setAppName("my_test", UserHandle.myUserId());
        //Debug.waitForDebugger();
        //Looper.prepare();
        Looper.prepareMainLooper();
        Log.i("dengsam","Run...111222");
        // 获取WMS服务
        IWindowManager wms = IWindowManager.Stub.asInterface(
                ServiceManager.getService(Context.WINDOW_SERVICE));
        // 通过WindowManagerGlobal获取进程唯一的IWindowSession实例。它将用于向WMS
        // 发送请求。注意这个函数在较早的Android版本（如4.1）位于ViewRootImpl类中
        Log.i("dengsam","Run...222");
        mSession = WindowManagerGlobal.getWindowSession();
        // 获取屏幕分辨率
        Log.i("dengsam","Run...333");
        IDisplayManager dm = IDisplayManager.Stub.asInterface(
                ServiceManager.getService(Context.DISPLAY_SERVICE));
        Log.i("dengsam","Run...444");
        DisplayInfo di = dm.getDisplayInfo(Display.DEFAULT_DISPLAY);
        Point scrnSize = new Point(di.appWidth, di.appHeight);
        // 初始化WindowManager.LayoutParams
        initLayoutParams(scrnSize);
        // 将新窗口添加到WMS
        installWindow(wms);
        // 初始化Choreographer的实例，此实例为线程唯一。这个类的用法与Handler
        // 类似，不过它总是在VSYC同步时回调，所以比Handler更适合做动画的循环器
        mChoreographer = Choreographer.getInstance();
        // 开始处理第一帧的动画
        scheduleNextFrame();
        // 当前线程陷入消息循环，直到Looper.quit()
        Looper.loop();
        // 标记不要继续绘制动画帧
        mContinueAnime = false;
        // 卸载当前Window
        uninstallWindow(wms);
    }

    public void initLayoutParams(Point screenSize) {
        // 标记即将安装的窗口类型为SYSTEM_ALERT
        mLp.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        mLp.setTitle("SampleWindow");
        // 设定窗口的左上角坐标以及高度和宽度
        mLp.gravity = Gravity.LEFT | Gravity.TOP;
        mLp.x = screenSize.x / 4;
        mLp.y = screenSize.y / 4;
        mLp.width = screenSize.x / 2;
        mLp.height = screenSize.y / 2;
        // 和输入事件相关的Flag，希望当输入事件发生在此窗口之外时，其他窗口也可以接收输入事件
        mLp.flags = mLp.flags | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
    }

    public void installWindow(IWindowManager wms) throws Exception {
        // 首先向WMS声明一个Token，任何一个Window都需要隶属于一个特定类型的Token
        wms.addWindowToken(mToken, WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,0);
        // 设置窗口所隶属的Token
        mLp.token = mToken;
        // 通过IWindowS
        // 目前仍然没有有效的Surface。不过，经过这个调用后，mInputChannel已经可以用来接收
        // 输入事件了
         mSession.add(mWindow, 0, mLp, View.VISIBLE, mOverscanInsets, mStableInsets, mInputChannel);
        /*通过IWindowSession要求WMS对本窗口进行重新布局，经过这个操作后，WMS将会为窗口
          创建一块用于绘制的Surface并保存在参数mSurface中。同时，这个Surface被WMS放置在
          LayoutParams所指定的位置上 */
        final DisplayCutout.ParcelableWrapper tmpCutout = new DisplayCutout.ParcelableWrapper();
        mSession.relayout(mWindow, 0, mLp, mLp.width, mLp.height, View.VISIBLE,0,-1,
        	mWinFrame, mOverscanInsets, mContentInsets,mVisibleInsets, mStableInsets, mOutsets, mBackdropFrame,tmpCutout, mConfig, mSurface);
        if (!mSurface.isValid()) {
            throw new RuntimeException("Failed creating Surface.");
        }
        // 基于WMS返回的InputChannel创建一个Handler，用于监听输入事件
        // mInputHandler一旦被创建，就已经在监听输入事件了
        mInputHandler = new InputHandler(mInputChannel, Looper.myLooper());
    }

    public void uninstallWindow(IWindowManager wms) throws Exception {
        // 从WMS处卸载窗口
        mSession.remove(mWindow);
        // 从WMS处移除之前添加的Token
        wms.removeWindowToken(mToken,0);
    }

    public void scheduleNextFrame() {
        // 要求在显示系统刷新下一帧时回调mFrameRender，注意，只回调一次
        mChoreographer.postCallback(Choreographer.CALLBACK_ANIMATION
                , mFrameRender, null);
    }

    // // 这个Runnable对象用于在窗口上描绘一帧
    public Runnable mFrameRender = new Runnable() {
        @Override
        public void run() {
            try {
                /*
                 * 方法 说明 drawRect 绘制矩形 drawCircle 绘制圆形 drawOval 绘制椭圆 drawPath 绘制任意多边形
                 * drawLine 绘制直线 drawPoin 绘制点
                 */
                // 创建画笔
                Paint p = new Paint();
                p.setColor(Color.RED);// 设置红色
                Canvas canvas = mSurface.lockCanvas(null);
                //canvas.drawText("画圆：", 10, 20, p);// 画文本
                canvas.drawCircle(60, 20, 10, p);// 小圆
                p.setAntiAlias(true);// 设置画笔的锯齿效果。 true是去除，大家一看效果就明白了
                canvas.drawCircle(120, 20, 20, p);// 大圆

                //canvas.drawText("画线及弧线：", 10, 60, p);
                p.setColor(Color.GREEN);// 设置绿色
                canvas.drawLine(60, 40, 100, 40, p);// 画线
                canvas.drawLine(110, 40, 190, 80, p);// 斜线
                //画笑脸弧线
                p.setStyle(Paint.Style.STROKE);//设置空心
                RectF oval1=new RectF(150,20,180,40);
                canvas.drawArc(oval1, 180, 180, false, p);//小弧形
                oval1.set(190, 20, 220, 40);
                canvas.drawArc(oval1, 180, 180, false, p);//小弧形
                oval1.set(160, 30, 210, 60);
                canvas.drawArc(oval1, 0, 180, false, p);//小弧形

                //canvas.drawText("画矩形：", 10, 80, p);
                p.setColor(Color.GRAY);// 设置灰色
                p.setStyle(Paint.Style.FILL);//设置填满
                canvas.drawRect(60, 60, 80, 80, p);// 正方形
                canvas.drawRect(60, 90, 160, 100, p);// 长方形

                //canvas.drawText("画扇形和椭圆:", 10, 120, p);
                /* 设置渐变色 这个正方形的颜色是改变的 */
                Shader mShader = new LinearGradient(0, 0, 100, 100,
                        new int[] { Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW,
                                Color.LTGRAY }, null, Shader.TileMode.REPEAT); // 一个材质,打造出一个线性梯度沿著一条线。
                p.setShader(mShader);
                // p.setColor(Color.BLUE);
                RectF oval2 = new RectF(60, 100, 200, 240);// 设置个新的长方形，扫描测量
                canvas.drawArc(oval2, 200, 130, true, p);
                // 画弧，第一个参数是RectF：该类是第二个参数是角度的开始，第三个参数是多少度，第四个参数是真的时候画扇形，是假的时候画弧线
                //画椭圆，把oval改一下
                oval2.set(210,100,250,130);
                canvas.drawOval(oval2, p);

                //canvas.drawText("画三角形：", 10, 200, p);
                // 绘制这个三角形,你可以绘制任意多边形
                Path path = new Path();
                path.moveTo(80, 200);// 此点为多边形的起点
                path.lineTo(120, 250);
                path.lineTo(80, 250);
                path.close(); // 使这些点构成封闭的多边形
                canvas.drawPath(path, p);

                // 你可以绘制很多任意多边形，比如下面画六连形
                p.reset();//重置
                p.setColor(Color.LTGRAY);
                p.setStyle(Paint.Style.STROKE);//设置空心
                Path path1=new Path();
                path1.moveTo(180, 200);
                path1.lineTo(200, 200);
                path1.lineTo(210, 210);
                path1.lineTo(200, 220);
                path1.lineTo(180, 220);
                path1.lineTo(170, 210);
                path1.close();//封闭
                canvas.drawPath(path1, p);
                /*
                 * Path类封装复合(多轮廓几何图形的路径
                 * 由直线段*、二次曲线,和三次方曲线，也可画以油画。drawPath(路径、油漆),要么已填充的或抚摸
                 * (基于油漆的风格),或者可以用于剪断或画画的文本在路径。
                 */

                //画圆角矩形
                p.setStyle(Paint.Style.FILL);//充满
                p.setColor(Color.LTGRAY);
                p.setAntiAlias(true);// 设置画笔的锯齿效果
                //canvas.drawText("画圆角矩形:", 10, 260, p);
                RectF oval3 = new RectF(80, 260, 200, 300);// 设置个新的长方形
                canvas.drawRoundRect(oval3, 20, 15, p);//第二个参数是x半径，第三个参数是y半径

                //画贝塞尔曲线
                //canvas.drawText("画贝塞尔曲线:", 10, 310, p);
                p.reset();
                p.setStyle(Paint.Style.STROKE);
                p.setColor(Color.GREEN);
                Path path2=new Path();
                path2.moveTo(100, 320);//设置Path的起点
                path2.quadTo(150, 310, 170, 400); //设置贝塞尔曲线的控制点坐标和终点坐标
                canvas.drawPath(path2, p);//画出贝塞尔曲线

                //画点
                p.setStyle(Paint.Style.FILL);
                //canvas.drawText("画点：", 10, 390, p);
                canvas.drawPoint(60, 390, p);//画一个点
                canvas.drawPoints(new float[]{60,400,65,400,70,400}, p);//画多个点
                mSurface.unlockCanvasAndPost(canvas);
                mSession.finishDrawing(mWindow);
            if (mContinueAnime)
                    scheduleNextFrame();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    // // 定义一个类继承InputEventReceiver，用于在其onInputEvent()函数中接收窗口的输入事件
    class InputHandler extends InputEventReceiver {
        Looper mLooper = null;
        public InputHandler(InputChannel inputChannel, Looper looper) {
            super(inputChannel, looper);
            mLooper = looper;
        }
        @Override
        public void onInputEvent(InputEvent event,int displayId) {
            if (event instanceof MotionEvent) {
                MotionEvent me = (MotionEvent)event;
                if (me.getAction() == MotionEvent.ACTION_UP) {
                    // 退出程序
                    mLooper.quit();
                }
            }else if(event instanceof KeyEvent){
                KeyEvent key = (KeyEvent)event;
                if(key.getKeyCode() == KeyEvent.KEYCODE_BACK){
                    // 退出程序
                    mLooper.quit();
                }
            }
            super.onInputEvent(event,displayId);
        }
    }

    // // 实现一个继承自IWindow.Stub的类MyWindow
    static class MyWindow extends IWindow.Stub {
        // 保持默认的实现即可

    @Override
    public void resized(Rect frame, Rect overscanInsets, Rect contentInsets, Rect visibleInsets,
            Rect stableInsets, Rect outsets, boolean reportDraw, MergedConfiguration newConfig,
            Rect backDropFrame, boolean forceLayout, boolean alwaysConsumeNavBar,int displayId,DisplayCutout.ParcelableWrapper displayCutout) {

    }
    @Override
    public void moved(int newX, int newY) {
    }

    @Override
    public void dispatchAppVisibility(boolean visible) {
    }

    @Override
    public void dispatchGetNewSurface() {
    }

    @Override
    public void windowFocusChanged(boolean hasFocus, boolean touchEnabled) {
    }



    @Override
    public void closeSystemDialogs(String reason) {
    }

    @Override
    public void dispatchWallpaperOffsets(float x, float y, float xStep, float yStep, boolean sync) {

    }

    @Override
    public void dispatchDragEvent(DragEvent event) {
    }

    @Override
    public void updatePointerIcon(float x, float y) {
    }

    @Override
    public void dispatchSystemUiVisibilityChanged(int seq, int globalUi,
            int localValue, int localChanges) {
    }

    @Override
    public void dispatchWallpaperCommand(String action, int x, int y,
            int z, Bundle extras, boolean sync) {

    }

    @Override
    public void dispatchWindowShown() {
    }

    @Override
    public void requestAppKeyboardShortcuts(IResultReceiver receiver, int deviceId) {

    }

    @Override
    public void executeCommand(String command, String parameters, ParcelFileDescriptor out) {

    }
    @Override
    public void dispatchPointerCaptureChanged(boolean hasCapture) {

    }
    }
}

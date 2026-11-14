package com.stevengc360.rescatetempestad;

import android.content.Context;
import android.graphics.*;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.view.*;

/** All mutation stays on the UI thread; the engine has no Android dependency. */
public final class GameView extends View implements Choreographer.FrameCallback {
    private static final int BG=0xff080b12,WHITE=0xffe7eae1,GOLD=0xffe9c884,MUTED=0xff8c9da5,RED=0xfff57370;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint vig=new Paint();
    private final Path path=new Path();
    private final RectF rect=new RectF();
    private final LinearGradient sky=new LinearGradient(0,0,0,720,new int[]{0xff10203a,0xff080b12},null,Shader.TileMode.CLAMP);
    private final LinearGradient groundGrad=new LinearGradient(0,415,0,720,new int[]{0xff242e39,0xff12171d},null,Shader.TileMode.CLAMP);
    private final RadialGradient vignette=new RadialGradient(640,360,780,new int[]{0x00000000,0x59000000},new float[]{.62f,1f},Shader.TileMode.CLAMP);
    private final GameStore store;
    private ToneGenerator tone;
    private GameEngine game,saved;
    private boolean menu=true,frames,completed;
    private long lastFrame;
    private double accumulator,camera,visual,autoSave;
    private float scale=1,ox,oy,ix,iy;
    private int left=-1,lower=-1,raise=-1,safeL,safeR,safeT,safeB,lastCabin;

    public GameView(Context context){
        super(context);vig.setShader(vignette);store=new GameStore(context);saved=store.load();setFocusable(true);
        setContentDescription("Rescate Tempestad. Joystick izquierdo para volar. Botones de cuerda a la derecha.");
        try{tone=new ToneGenerator(AudioManager.STREAM_MUSIC,30);}catch(RuntimeException unavailable){tone=null;}
    }
    public void start(){if(!frames){frames=true;lastFrame=0;Choreographer.getInstance().postFrameCallback(this);}}
    public void stop(){frames=false;lastFrame=0;Choreographer.getInstance().removeFrameCallback(this);}
    public void release(){stop();if(tone!=null){tone.release();tone=null;}}
    public void suspend(){clearInput();if(!menu&&game!=null){game.pause();store.save(game);saved=store.load();}}
    public void back(){if(menu)return;if(game.phase==GameEngine.Phase.RUNNING)suspend();else{if(game.phase==GameEngine.Phase.PAUSED)store.save(game);saved=store.load();menu=true;clearInput();}}
    private void clearInput(){left=lower=raise=-1;ix=iy=0;accumulator=0;}
    private void launch(int index){game=new GameEngine(index);game.pause();menu=false;completed=false;camera=0;lastCabin=0;autoSave=0;clearInput();store.save(game);saved=store.load();}
    GameEngine state(){return game;}
    boolean inMenu(){return menu;}
    float screenX(float x){return ox+x*scale;}
    float screenY(float y){return oy+y*scale;}
    float inputX(){return ix;}
    int winchInput(){return (lower>=0?1:0)-(raise>=0?1:0);}
    @Override public void doFrame(long now){
        if(!frames)return;double dt=lastFrame==0?0:Math.min(.1,(now-lastFrame)/1e9);lastFrame=now;visual+=dt;
        if(!menu&&game!=null&&game.phase==GameEngine.Phase.RUNNING){
            accumulator+=dt;int steps=0;
            while(accumulator>=GameEngine.STEP&&steps++<6){game.tick(GameEngine.STEP,ix,iy,winchInput());accumulator-=GameEngine.STEP;}
            if(game.phase==GameEngine.Phase.WON&&!completed){store.complete(game);saved=null;completed=true;clearInput();beep(ToneGenerator.TONE_PROP_ACK);}
            if(game.phase==GameEngine.Phase.LOST){store.clear();saved=null;clearInput();}
            camera+=(GameEngine.clamp(game.x-550,0,Math.max(0,game.mission.width-1280))-camera)*Math.min(1,dt*4);
            if(game.cabin>lastCabin)beep(ToneGenerator.TONE_PROP_BEEP);lastCabin=game.cabin;
            autoSave+=dt;if(autoSave>=10&&game.phase==GameEngine.Phase.RUNNING){store.save(game);autoSave=0;}
        }else accumulator=0;
        invalidate();Choreographer.getInstance().postFrameCallback(this);
    }
    private void beep(int kind){if(store.sound()&&tone!=null)tone.startTone(kind,140);}
    @Override public WindowInsets onApplyWindowInsets(WindowInsets insets){
        if(android.os.Build.VERSION.SDK_INT>=30){Insets a=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());safeL=a.left;safeR=a.right;safeT=a.top;safeB=a.bottom;layoutViewport(getWidth(),getHeight());}
        else {safeL=insets.getSystemWindowInsetLeft();safeR=insets.getSystemWindowInsetRight();safeT=insets.getSystemWindowInsetTop();safeB=insets.getSystemWindowInsetBottom();layoutViewport(getWidth(),getHeight());}
        return super.onApplyWindowInsets(insets);
    }
    @Override protected void onSizeChanged(int w,int h,int a,int b){layoutViewport(w,h);}
    private void layoutViewport(int w,int h){scale=Math.max(.001f,Math.min((w-safeL-safeR)/1280f,(h-safeT-safeB)/720f));ox=safeL+(w-safeL-safeR-1280*scale)/2;oy=safeT+(h-safeT-safeB-720*scale)/2;}
    private void stroke(int color,float width){p.setStyle(Paint.Style.STROKE);p.setColor(color);p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);}
    private void line(Canvas c,double x,double y,double xx,double yy,int color,float width){stroke(color,width);c.drawLine((float)x,(float)y,(float)xx,(float)yy,p);}
    private void circle(Canvas c,double x,double y,double r,int color,float width){stroke(color,width);c.drawCircle((float)x,(float)y,(float)r,p);}
    private void box(Canvas c,float x,float y,float w,float h,int color,float radius){p.setStyle(Paint.Style.FILL);p.setColor(color);rect.set(x,y,x+w,y+h);c.drawRoundRect(rect,radius,radius,p);}
    private void text(Canvas c,String s,float x,float y,float size,int color,boolean bold){p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTextSize(size);p.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);c.drawText(s,x,y,p);}
    private void button(Canvas c,float x,float y,float w,float h,String s,boolean active){box(c,x,y,w,h,active?0xff27333b:0xff151d25,11);stroke(active?GOLD:0xff455663,1.5f);rect.set(x,y,x+w,y+h);c.drawRoundRect(rect,11,11,p);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(18);float tx=x+(w-p.measureText(s))/2;text(c,s,tx,y+h/2+6,18,active?GOLD:WHITE,true);}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.drawColor(BG);int save=canvas.save();canvas.translate(ox,oy);canvas.scale(scale,scale);canvas.clipRect(0,0,1280,720);fill.setShader(sky);fill.setStyle(Paint.Style.FILL);canvas.drawRect(0,0,1280,720,fill);fill.setShader(null);if(menu)drawMenu(canvas);else if(game!=null){world(canvas);hud(canvas);if(game.phase!=GameEngine.Phase.RUNNING)overlay(canvas);}canvas.restoreToCount(save);}
    private void drawMenu(Canvas c){
        clouds(c,5);rain(c,45);lightning(c);text(c,"STEVENGC360  /  OPERACIONES DE RESCATE",64,56,15,MUTED,true);
        text(c,"RESCATE TEMPESTAD",64,132,58,GOLD,true);
        line(c,64,152,330,152,GOLD,2.5f);
        text(c,"Domina el vuelo y la cuerda. Saca a todos de la tormenta.",67,174,21,WHITE,false);
        button(c,64,200,510,54,saved!=null?"CONTINUAR OPERACIÓN GUARDADA":"ELIGE UNA OPERACIÓN",saved!=null);
        text(c,"0.2.1  ·  GUARDADO AUTOMÁTICO LOCAL",630,234,16,MUTED,false);
        for(int i=0;i<6;i++){float x=64+i%3*390,y=285+i/3*114;boolean open=i<=store.unlocked();box(c,x,y,366,94,0xff121921,12);text(c,"0"+(i+1),x+18,y+37,29,open?GOLD:MUTED,true);text(c,open?GameEngine.MISSIONS[i].name:"BLOQUEADA",x+70,y+34,19,open?WHITE:MUTED,true);text(c,open?"SOS "+GameEngine.MISSIONS[i].target+"  ·  "+store.stars(i)+"/3 estrellas":"Completa la anterior",x+70,y+66,16,MUTED,false);}
        button(c,64,533,240,53,store.sound()?"SONIDO: SÍ":"SONIDO: NO",store.sound());button(c,325,533,300,53,store.calm()?"EFECTOS: SUAVES":"EFECTOS: NORMALES",store.calm());
        text(c,"VOLAR: joystick izquierdo.  BAJAR: cuerda.  RECOGER: sube al superviviente.",64,628,19,WHITE,false);
        text(c,"Evacúa sobre BASE, a baja altura y velocidad. Cabina: 3 plazas.",64,660,18,MUTED,false);
        if(!store.warning.isEmpty())text(c,store.warning,64,698,15,RED,false);
        int hs=c.save();c.translate(1015,210);c.rotate(-7);c.scale(1.1f,1.1f);heliBody(c,3);c.restoreToCount(hs);
        c.drawRect(0,0,1280,720,vig);
    }
    private void world(Canvas c){
        clouds(c,6);
        for(int layer=0;layer<3;layer++){path.reset();path.moveTo(0,720);for(int sx=0;sx<=1280;sx+=16){double wx=sx+camera*(.15+layer*.2);path.lineTo(sx,(float)(415+layer*52+50*Math.sin(wx*.004+layer*2)+21*Math.sin(wx*.01)));}path.lineTo(1280,720);path.close();fill.setStyle(Paint.Style.FILL);fill.setColor(0xff0e141c+layer*0x030303);c.drawPath(path,fill);}
        path.reset();path.moveTo(-20,720);for(int sx=-20;sx<=1300;sx+=8)path.lineTo(sx,(float)game.ground(sx+camera));path.lineTo(1300,720);path.close();fill.setStyle(Paint.Style.FILL);fill.setShader(groundGrad);c.drawPath(path,fill);fill.setShader(null);
        for(int sx=0;sx<1280;sx+=8)line(c,sx,game.ground(sx+camera),sx+8,game.ground(sx+8+camera),MUTED,1.5f);
        float bx=(float)(180-camera);
        fill.setStyle(Paint.Style.FILL);fill.setColor(0x4de9c884);rect.set(bx-92,543,bx+92,556);c.drawRoundRect(rect,7,7,fill);
        line(c,bx-100,550,bx+100,550,GOLD,3);
        fill.setStyle(Paint.Style.FILL);fill.setColor(0xff131a23);c.drawCircle(bx,538,15,fill);
        circle(c,bx,538,15,GOLD,2);text(c,"H",bx-6,544,17,GOLD,true);text(c,"BASE",bx-23,590,16,GOLD,true);
        circle(c,bx-100,550,3.5+2*Math.sin(visual*4),GOLD,1.5f);circle(c,bx+100,550,3.5+2*Math.cos(visual*4),GOLD,1.5f);
        if(game.mission.tornado>0)tornado(c);
        for(GameEngine.Person q:game.people)if(q.status==GameEngine.Status.WAITING||q.status==GameEngine.Status.HANGING){
            if(q.status==GameEngine.Status.WAITING){double gy=game.ground(q.x);double h=Math.max(0,gy-q.y);fill.setStyle(Paint.Style.FILL);fill.setColor(Color.argb((int)Math.max(0,60-h*.3),0,0,0));rect.set((float)(q.x-camera-9),(float)gy-3,(float)(q.x-camera+9),(float)gy+3);c.drawOval(rect,fill);}
            person(c,q.x-camera,q.y,q.status==GameEngine.Status.HANGING?GOLD:WHITE);if(q.status==GameEngine.Status.WAITING){circle(c,q.x-camera,q.y-38,10+3*Math.sin(visual*3),GOLD,1);text(c,"SOS",(float)(q.x-camera-14),(float)q.y-57,13,GOLD,true);}else text(c,(int)Math.ceil(q.grip)+"s",(float)(q.x-camera+14),(float)q.y,13,GOLD,true);}
        if(!game.broken){for(int i=1;i<GameEngine.N;i++)line(c,game.rx[i-1]-camera,game.ry[i-1],game.rx[i]-camera,game.ry[i],game.tension>.8?RED:WHITE,2);circle(c,game.rx[13]-camera,game.ry[13],5,GOLD,2);}
        double gy2=game.ground(game.x),hh=Math.max(0,gy2-game.y);
        fill.setStyle(Paint.Style.FILL);fill.setColor(Color.argb((int)Math.max(0,64-hh*.2),0,0,0));rect.set((float)(game.x-camera-hh*.09),(float)gy2-3,(float)(game.x-camera+hh*.09),(float)gy2+3);c.drawOval(rect,fill);
        helicopter(c);rain(c,store.calm()?40:115);lightning(c);
        c.drawRect(0,0,1280,720,vig);
    }
    private void person(Canvas c,double x,double y,int col){circle(c,x,y-11,4,col,1.8f);line(c,x,y-6,x,y+7,col,1.8f);line(c,x,y-1,x-8,y-8+Math.sin(visual*6)*3,col,1.5f);line(c,x,y-1,x+8,y-8,col,1.5f);line(c,x,y+7,x-5,y+16,col,1.5f);line(c,x,y+7,x+6,y+16,col,1.5f);}
    private void helicopter(Canvas c){int save=c.save();c.translate((float)(game.x-camera),(float)game.y);c.rotate((float)(GameEngine.clamp(game.vx/500,-.38,.38)*57.3));heliBody(c,game.cabin);c.restoreToCount(save);}
    private void heliBody(Canvas c,int cabin){
        fill.setStyle(Paint.Style.FILL);fill.setColor(0x22e9c884);path.reset();path.moveTo(16,8);path.lineTo(165,215);path.lineTo(70,222);path.close();c.drawPath(path,fill);
        box(c,-30,-16,60,32,0xff343d3f,10);stroke(game!=null&&game.health<35?RED:WHITE,2);rect.set(-30,-16,30,16);c.drawRoundRect(rect,10,10,p);
        box(c,8,-11,15,18,GOLD,3);line(c,-10,-13,-10,13,WHITE,1.5f);line(c,-30,-2,-67,-13,WHITE,2);line(c,-67,-13,-70,-30,WHITE,2);line(c,-75,-29,-61,-29,WHITE,2);line(c,-18,17,-21,26,WHITE,2);line(c,18,17,21,26,WHITE,2);line(c,-34,27,34,27,WHITE,2);line(c,-2,-16,-2,-29,WHITE,2);
        double blade=48+13*Math.cos(visual*35);line(c,-blade,-30,blade,-30,WHITE,3);line(c,-68,-21,-68+Math.cos(visual*35)*11,-21+Math.sin(visual*35)*11,GOLD,2);
        for(int i=0;i<cabin;i++)circle(c,-20+i*10,3,2,GOLD,2);
    }
    private void tornado(Canvas c){double tx=game.tornadoX-camera;for(int i=0;i<30;i++){double y=105+i*15,r=38+(1-i/30.0)*126,a=visual*4+i*.63,center=tx+Math.sin(a*.6)*15;stroke(Color.argb(70+i*3,162,174,180),1.4f);rect.set((float)(center-r),(float)(y-12),(float)(center+r),(float)(y+12));c.drawArc(rect,(float)(a*57),245,false,p);}text(c,dangerLabelText(tx),dangerLabelX(tx),91,15,RED,true);}
    String dangerLabelText(double tx){return tx<0?"< PELIGRO":tx>1280?"PELIGRO >":"PELIGRO";}
    float dangerLabelX(double tx){p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(15);float w=p.measureText(dangerLabelText(tx));return (float)GameEngine.clamp(tx-w/2,12,1268-w);}
    private void rain(Canvas c,int count){for(int i=0;i<count;i++){double x=((i*137.51+visual*95-camera*.14)%1370+1370)%1370-40,y=(i*79.17+visual*(280+i%5*35))%730-20;line(c,x,y,x+8,y+20,Color.argb(55+i%4*15,180,193,202),1);}}
    private void clouds(Canvas c,int n){for(int i=0;i<n;i++){double x=((i*211.7+visual*(6+(i%3)*4))%1500+1500)%1500-110,y=16+((i*53)%64);fill.setStyle(Paint.Style.FILL);fill.setColor(i%2==0?0xb30c141e:0xb3111926);c.drawOval((float)x,(float)y,(float)(x+230+(i%3)*100),(float)(y+30),fill);}}
    private void lightning(Canvas c){if(store.calm())return;double f=Math.max(0,Math.sin((visual*.31%1)*Math.PI*2)-.92)/.08;if(f<=0)return;fill.setStyle(Paint.Style.FILL);fill.setColor(Color.argb((int)(44*f),225,238,255));c.drawRect(0,0,1280,720,fill);if(f>.45){double x=120+(Math.floor(visual*.31)*317%1040);stroke(0xffe2ecf8,2);c.drawLine((float)x,0,(float)(x-15),120,p);c.drawLine((float)(x-15),120,(float)(x+18),215,p);c.drawLine((float)(x+18),215,(float)(x-9),340,p);}}
    private void bar(Canvas c,int x,String title,double v,int color){text(c,title,x,23,12,MUTED,true);box(c,x,32,110,5,0xff343e45,2);box(c,x,32,(float)(110*GameEngine.clamp(v,0,1)),5,color,2);}
    private void hud(Canvas c){box(c,0,0,1280,69,0xee080b12,0);text(c,"OPERACIÓN "+(game.index+1)+" / "+game.mission.name,22,27,17,WHITE,true);text(c,"EVACUADOS "+game.delivered+"/"+game.mission.target+"    A BORDO "+game.cabin+"/3",22,53,15,GOLD,true);box(c,372,42,180,8,0xff343e45,4);box(c,372,42,(float)(180*game.delivered/Math.max(1,game.mission.target)),8,GOLD,4);bar(c,590,"INTEGRIDAD",game.health/100,game.health<35?RED:GOLD);bar(c,725,"COMBUSTIBLE",game.fuel/100,GOLD);bar(c,860,"TENSIÓN",game.tension,game.tension>.8?RED:WHITE);text(c,String.format(java.util.Locale.ROOT,"%02d:%02d",(int)game.time/60,(int)game.time%60),1015,45,24,WHITE,true);button(c,1140,12,118,44,"PAUSA",false);
        if(!game.message.isEmpty()){box(c,245,82,790,38,0xdd080b12,8);text(c,game.message,265,108,17,GOLD,false);}
        if(!store.warning.isEmpty())text(c,store.warning,265,144,15,RED,false);
        box(c,0,599,1280,121,0xdd080b12,0);circle(c,135,647,52,0x99dce6eb,2);circle(c,135+ix*36,647+iy*36,21,GOLD,2);text(c,"VOLAR",108,715,12,MUTED,true);button(c,920,621,145,70,"BAJAR",lower>=0);button(c,1085,621,173,70,"RECOGER",raise>=0);
        double target=180,best=Double.MAX_VALUE;if(game.cabin==0)for(GameEngine.Person q:game.people)if(q.status==GameEngine.Status.WAITING&&Math.abs(q.x-game.x)<best){best=Math.abs(q.x-game.x);target=q.x;}
        text(c,(target<game.x?"<  ":"")+(game.cabin>0?"BASE":"SOS")+"  "+(int)Math.abs(target-game.x)+" m"+(target>game.x?"  >":""),330,647,21,GOLD,true);text(c,game.broken?"ROTA: BAJAR PARA REPONER":"CUERDA "+(int)game.rope+" · RECOGER PARA SUBIR",330,677,14,MUTED,false);
    }
    private void overlay(Canvas c){boolean paused=game.phase==GameEngine.Phase.PAUSED,won=game.phase==GameEngine.Phase.WON;box(c,0,0,1280,720,0xcc03060a,0);box(c,270,170,740,380,0xff121921,18);stroke(won?GOLD:0xff2a3540,2);rect.set(270,170,1010,550);c.drawRoundRect(rect,18,18,p);text(c,paused?"OPERACIÓN EN PAUSA":won?"TODOS A SALVO":"RESCATE INTERRUMPIDO",315,233,30,won?GOLD:WHITE,true);
        String body=paused?"1. Vuela hasta una baliza SOS y baja la cuerda.\n2. Estabiliza, engancha y mantén RECOGER.\n3. Regresa a BASE y desciende despacio.":won?"Has evacuado a "+game.delivered+" personas.\nValoración: "+game.stars()+" de 3 estrellas.":game.failure;
        int row=0;for(String s:body.split("\n"))text(c,s,315,283+row++*30,19,MUTED,false);
        if(won)for(int i=0;i<3;i++)text(c,"★",315+i*36,355,32,i<game.stars()?GOLD:0xff343e45,true);
        button(c,315,400,290,57,paused?"CONTINUAR":won&&game.index<5?"SIGUIENTE":"REINTENTAR",true);button(c,630,400,290,57,"GUARDAR Y MENÚ",false);text(c,"Viento fuerte: vuela alto y recoge la cuerda al viajar.",315,511,16,GOLD,false);
    }
    private static boolean inside(float x,float y,float l,float t,float w,float h){return x>=l&&x<=l+w&&y>=t&&y<=t+h;}
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public boolean onTouchEvent(MotionEvent event){int action=event.getActionMasked(),a=event.getActionIndex(),id=event.getPointerId(a);float x=(event.getX(a)-ox)/scale,y=(event.getY(a)-oy)/scale;
        if(action==MotionEvent.ACTION_CANCEL){clearInput();return true;}
        if((action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN)&&!inside(x,y,0,0,1280,720))return true;
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){
            if(menu&&action==MotionEvent.ACTION_DOWN){
                if(saved!=null&&inside(x,y,64,200,510,54)){game=saved;saved=null;game.pause();menu=false;completed=false;lastCabin=game.cabin;autoSave=0;camera=GameEngine.clamp(game.x-550,0,Math.max(0,game.mission.width-1280));clearInput();}
                else{for(int i=0;i<6;i++)if(i<=store.unlocked()&&inside(x,y,64+i%3*390,285+i/3*114,366,94)){launch(i);return true;}if(inside(x,y,64,533,240,53))store.toggleSound();if(inside(x,y,325,533,300,53))store.toggleCalm();}
            }else if(!menu&&game.phase!=GameEngine.Phase.RUNNING&&action==MotionEvent.ACTION_DOWN){
                if(inside(x,y,315,400,290,57)){if(game.phase==GameEngine.Phase.PAUSED){clearInput();game.resume();}else launch(game.phase==GameEngine.Phase.WON&&game.index<5?game.index+1:game.index);}
                else if(inside(x,y,630,400,290,57)){store.save(game);saved=store.load();menu=true;clearInput();}
            }else if(!menu&&game.phase==GameEngine.Phase.RUNNING){
                if(inside(x,y,1140,12,118,44))suspend();else if(x<300&&y>515&&left<0){left=id;stick(x,y);}else if(inside(x,y,920,621,145,70)&&lower<0)lower=id;else if(inside(x,y,1085,621,173,70)&&raise<0)raise=id;
            }performClick();
        }else if(action==MotionEvent.ACTION_MOVE&&left>=0){int i=event.findPointerIndex(left);if(i>=0)stick((event.getX(i)-ox)/scale,(event.getY(i)-oy)/scale);else{left=-1;ix=iy=0;}}
        else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP){if(id==left){left=-1;ix=iy=0;}if(id==lower)lower=-1;if(id==raise)raise=-1;}
        return true;
    }
    private void stick(float x,float y){double dx=(x-135)/52,dy=(y-647)/52,n=Math.max(1,Math.hypot(dx,dy));ix=(float)(dx/n);iy=(float)(dy/n);}
}

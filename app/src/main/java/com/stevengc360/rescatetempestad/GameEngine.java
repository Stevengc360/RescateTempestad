package com.stevengc360.rescatetempestad;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/** World pixels/seconds. Android-free, fixed-step, reproducible simulation. */
public final class GameEngine {
    public static final double STEP=1.0/60;
    public static final int N=14, CAPACITY=3;
    public enum Phase { RUNNING, PAUSED, WON, LOST }
    public enum Status { WAITING, HANGING, ABOARD, SAVED, LOST }
    public static final class Person {
        public double x,y,grip=32;
        public Status status=Status.WAITING;
        Person(double x,double y){this.x=x;this.y=y;}
    }
    public static final class Mission {
        public final String name;
        public final int target;
        public final double width,seconds,wind,tornado,speed;
        Mission(String n,int t,double w,double sec,double wind,double tornado,double speed){name=n;target=t;width=w;seconds=sec;this.wind=wind;this.tornado=tornado;this.speed=speed;}
    }
    public static final Mission[] MISSIONS={
        new Mission("Primer rescate",1,1550,240,3,-1,0),
        new Mission("Lluvia lateral",2,2100,250,16,-1,0),
        new Mission("El embudo",3,2650,300,24,1450,32),
        new Mission("Cuerda al límite",4,3100,340,30,1600,46),
        new Mission("Noche de evacuación",5,3500,390,36,1900,60),
        new Mission("Última llamada",6,3900,440,44,2100,72)
    };
    public final int index;
    public final Mission mission;
    public final List<Person> people=new ArrayList<>();
    public final double[] rx=new double[N],ry=new double[N];
    private final double[] ox=new double[N],oy=new double[N];
    public Phase phase=Phase.RUNNING;
    public double x=180,y=260,vx,vy,health=100,fuel=100,time,elapsed,rope=140,tension,tornadoX;
    public boolean broken;
    public int delivered,cabin;
    public String message="",failure="";
    private double messageTime,hitCooldown,spareCooldown,dockTime,overload,direction=1;

    public GameEngine(int index){
        this.index=Math.max(0,Math.min(5,index));mission=MISSIONS[this.index];time=mission.seconds;tornadoX=mission.tornado;
        for(int i=0;i<mission.target;i++){double px=700+i*(mission.width-1100)/Math.max(1,mission.target-1);people.add(new Person(px,ground(px)-18));}
        resetRope();say("Acerca la cuerda a SOS. Recoge y vuelve a BASE.",6);
    }
    public static double clamp(double v,double a,double b){return Math.max(a,Math.min(b,v));}
    public double ground(double px){return px<340?565:565+Math.min(1,(px-340)/150)*(-30+45*Math.sin(px*.0048)+17*Math.sin(px*.012));}
    public int count(Status s){int c=0;for(Person p:people)if(p.status==s)c++;return c;}
    public int stars(){return phase==Phase.WON?1+(health>=60?1:0)+(time>=mission.seconds*.25?1:0):0;}
    public void say(String s,double seconds){message=s;messageTime=seconds;}
    public void pause(){if(phase==Phase.RUNNING)phase=Phase.PAUSED;}
    public void resume(){if(phase==Phase.PAUSED)phase=Phase.RUNNING;}
    public double storm(double px,double py){return mission.tornado<0?0:Math.max(0,1-Math.abs(px-tornadoX)/170)*clamp((py-105)/180,0,1);}
    public double wind(double px,double py){
        double gust=mission.wind*(.55+.45*Math.sin(elapsed*.83+px*.002));
        double d=tornadoX-px;
        if(mission.tornado>0&&Math.abs(d)<240)gust+=d*1.9*(1-Math.abs(d)/240)*clamp((py-105)/180,0,1);
        return gust;
    }
    private void resetRope(){for(int i=0;i<N;i++){rx[i]=ox[i]=x;ry[i]=oy[i]=y+20+rope*i/(N-1);}}
    public void tick(double dt,double ix,double iy,int winch){
        if(phase!=Phase.RUNNING||!Double.isFinite(dt)||dt<=0)return;
        dt=Math.min(dt,1.0/30);ix=Double.isFinite(ix)?clamp(ix,-1,1):0;iy=Double.isFinite(iy)?clamp(iy,-1,1):0;winch=Math.max(-1,Math.min(1,winch));
        if(failIfNecessary())return;
        elapsed+=dt;time=Math.max(0,time-dt);messageTime-=dt;if(messageTime<=0)message="";
        hitCooldown=Math.max(0,hitCooldown-dt);spareCooldown=Math.max(0,spareCooldown-dt);
        if(mission.tornado>0){tornadoX+=direction*mission.speed*dt;if(tornadoX>mission.width-370){tornadoX=mission.width-370;direction=-1;}if(tornadoX<900){tornadoX=900;direction=1;}}
        if(broken&&winch>0&&spareCooldown==0){broken=false;rope=90;resetRope();say("Cuerda de reserva lista",3);}
        if(!broken)rope=clamp(rope+winch*95*dt,65,245);
        vx=clamp(vx+(ix*310+wind(x,y)-vx*1.45)*dt,-240,240);
        vy=clamp(vy+(iy*300-vy*1.65-storm(x,y)*145)*dt,-220,220);
        x=clamp(x+vx*dt,60,mission.width-60);y+=vy*dt;
        if((x<=60&&vx<0)||(x>=mission.width-60&&vx>0))vx=0;
        if(y<85){y=85;vy=Math.max(0,vy);}
        double floor=ground(x)-27;
        if(y>floor){double impact=Math.hypot(vx,vy);if(impact>85&&hitCooldown==0){health-=Math.min(40,impact*.15);hitCooldown=1.2;say("Impacto: reduce la velocidad",3);}y=floor;vy=-Math.abs(vy)*.18;vx*=.92;}
        fuel=Math.max(0,fuel-dt*(.09+.04*Math.abs(ix)+.03*Math.abs(iy)));
        if(hitCooldown==0)health-=storm(x,y)*7*dt;
        // Failure must precede rescue, base repairs/refuelling and delivery.
        if(failIfNecessary())return;
        if(!broken){updateRope(dt);if(!broken)rescue(dt);}
        if(failIfNecessary())return;
        boolean docked=Math.abs(x-180)<100&&y>390&&Math.hypot(vx,vy)<85;
        if(docked){dockTime+=dt;health=Math.min(100,health+dt*12);fuel=Math.min(100,fuel+dt*22);if(cabin>0&&dockTime>.65){for(Person p:people)if(p.status==Status.ABOARD){p.status=Status.SAVED;delivered++;}cabin=0;say("Evacuación completada",4);}}else dockTime=0;
        health=clamp(health,0,100);
        if(delivered==mission.target){phase=Phase.WON;say("MISIÓN COMPLETADA",10);}
    }
    private boolean failIfNecessary(){
        if(health<=0){health=0;lose("Helicóptero inutilizado.");}
        else if(fuel<=0){fuel=0;lose("Sin combustible: reposta en BASE.");}
        else if(time<=0){time=0;lose("Tiempo de rescate agotado.");}
        else if(count(Status.LOST)>0)lose("Un superviviente se ha perdido. Reintenta.");
        return phase==Phase.LOST;
    }
    private void lose(String s){phase=Phase.LOST;failure=s;}
    private void updateRope(double dt){
        double segment=rope/(N-1),worst=1;
        for(int sub=0;sub<2;sub++){
            rx[0]=x;ry[0]=y+20;
            for(int i=1;i<N;i++){double px=rx[i],py=ry[i];rx[i]=px+(px-ox[i])*.991+wind(px,py)*dt*dt*.5;ry[i]=py+(py-oy[i])*.991+(410-storm(px,py)*540)*dt*dt*.25;ox[i]=px;oy[i]=py;}
            for(int pass=0;pass<14;pass++){
                rx[0]=x;ry[0]=y+20;
                for(int i=1;i<N;i++){double dx=rx[i]-rx[i-1],dy=ry[i]-ry[i-1],dist=Math.max(.00001,Math.hypot(dx,dy));if(pass==0)worst=Math.max(worst,dist/segment);double k=(dist-segment)/dist;if(i>1){rx[i-1]+=dx*k*.5;ry[i-1]+=dy*k*.5;}rx[i]-=dx*k*(i==1?1:.5);ry[i]-=dy*k*(i==1?1:.5);double floor=ground(rx[i])-4;if(ry[i]>floor){ry[i]=floor;oy[i]=floor;}}
            }
        }
        double risk=storm(rx[N-1],ry[N-1])*rope/245;
        tension+=(clamp((worst-1)*1.7+risk*.95,0,1.5)-tension)*Math.min(1,dt*3);
        overload=tension>1.04?overload+dt:Math.max(0,overload-dt*2);
        if(overload>.6&&index>=2){broken=true;spareCooldown=1.5;overload=tension=0;for(Person p:people)if(p.status==Status.HANGING){p.status=Status.WAITING;p.y=ground(p.x)-18;}say("¡Cuerda rota! Los colgados cayeron. Mantén BAJAR para reponer.",5);}
    }
    private void rescue(double dt){
        for(Person p:people){
            if(p.status==Status.WAITING&&cabin+count(Status.HANGING)<CAPACITY&&Math.hypot(p.x-rx[N-1],p.y-ry[N-1])<34&&Math.hypot(vx,vy)<125){p.status=Status.HANGING;say("Enganchado: mantén RECOGER",4);}
            if(p.status==Status.HANGING){p.x=rx[N-1];p.y=ry[N-1]+12;p.grip-=dt*(1+storm(p.x,p.y)*3);if(rope<=78){p.status=Status.ABOARD;cabin++;say("A bordo. Desciende sobre BASE para evacuar.",4);}else if(p.grip<=0)p.status=Status.LOST;}
        }
    }
    public boolean finite(){if(!Double.isFinite(x+y+vx+vy+health+fuel+time+elapsed+rope+tension+tornadoX))return false;for(int i=0;i<N;i++)if(!Double.isFinite(rx[i]+ry[i]+ox[i]+oy[i]))return false;for(Person p:people)if(!Double.isFinite(p.x+p.y+p.grip))return false;return true;}

    /** Schema 1 includes Verlet history: restoring positions alone changes rope momentum. */
    public byte[] snapshot() throws IOException {
        if(phase==null||message==null||failure==null||!valid())throw new IOException("Estado no válido para guardar");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.writeInt(0x52544331);out.writeInt(1);out.writeInt(index);out.writeInt(phase.ordinal());
        for(double v:new double[]{x,y,vx,vy,health,fuel,time,elapsed,rope,tension,tornadoX,messageTime,hitCooldown,spareCooldown,dockTime,overload,direction})out.writeDouble(v);
        out.writeBoolean(broken);out.writeInt(delivered);out.writeInt(cabin);out.writeUTF(message);out.writeUTF(failure);
        for(int i=0;i<N;i++){out.writeDouble(rx[i]);out.writeDouble(ry[i]);out.writeDouble(ox[i]);out.writeDouble(oy[i]);}
        out.writeInt(people.size());for(Person p:people){out.writeDouble(p.x);out.writeDouble(p.y);out.writeDouble(p.grip);out.writeInt(p.status.ordinal());}
        out.flush();byte[] payload=bytes.toByteArray();CRC32 crc=new CRC32();crc.update(payload);out.writeLong(crc.getValue());out.flush();return bytes.toByteArray();
    }
    public static GameEngine restore(byte[] bytes) throws IOException {
        if(bytes==null||bytes.length<24||bytes.length>16384)throw new IOException("Tamaño de guardado no válido");
        CRC32 crc=new CRC32();crc.update(bytes,0,bytes.length-8);DataInputStream checksum=new DataInputStream(new ByteArrayInputStream(bytes,bytes.length-8,8));if(checksum.readLong()!=crc.getValue())throw new IOException("Guardado dañado");
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes,0,bytes.length-8));
        if(in.readInt()!=0x52544331||in.readInt()!=1)throw new IOException("Versión de guardado incompatible");
        int idx=in.readInt(),phase=in.readInt();if(idx<0||idx>=MISSIONS.length||phase<0||phase>=Phase.values().length)throw new IOException("Estado no válido");
        GameEngine e=new GameEngine(idx);e.phase=Phase.values()[phase];
        e.x=in.readDouble();e.y=in.readDouble();e.vx=in.readDouble();e.vy=in.readDouble();e.health=in.readDouble();e.fuel=in.readDouble();e.time=in.readDouble();e.elapsed=in.readDouble();e.rope=in.readDouble();e.tension=in.readDouble();e.tornadoX=in.readDouble();e.messageTime=in.readDouble();e.hitCooldown=in.readDouble();e.spareCooldown=in.readDouble();e.dockTime=in.readDouble();e.overload=in.readDouble();e.direction=in.readDouble();
        e.broken=in.readBoolean();e.delivered=in.readInt();e.cabin=in.readInt();e.message=in.readUTF();e.failure=in.readUTF();
        for(int i=0;i<N;i++){e.rx[i]=in.readDouble();e.ry[i]=in.readDouble();e.ox[i]=in.readDouble();e.oy[i]=in.readDouble();}
        if(in.readInt()!=e.people.size())throw new IOException("Personas no válidas");
        for(Person p:e.people){p.x=in.readDouble();p.y=in.readDouble();p.grip=in.readDouble();int s=in.readInt();if(s<0||s>=Status.values().length)throw new IOException("Persona no válida");p.status=Status.values()[s];}
        if(in.available()!=0||!e.valid())throw new IOException("Datos de guardado fuera de límites");return e;
    }
    private boolean valid(){
        if(!finite()||x<60||x>mission.width-60||y<85||y>650||Math.abs(vx)>241||Math.abs(vy)>221||health<0||health>100||fuel<0||fuel>100||rope<65||rope>245||time<0||time>mission.seconds||elapsed<0||elapsed>86400||tension<0||tension>1.51||delivered!=count(Status.SAVED)||cabin!=count(Status.ABOARD)||cabin+count(Status.HANGING)>CAPACITY)return false;
        for(double v:new double[]{messageTime,hitCooldown,spareCooldown,dockTime,overload,direction})if(!Double.isFinite(v)||Math.abs(v)>86400)return false;
        if(direction!=1&&direction!=-1)return false;
        if(hitCooldown<0||spareCooldown<0||dockTime<0||overload<0||tornadoX< -1||tornadoX>mission.width)return false;
        if(phase==Phase.WON&&delivered!=mission.target)return false;
        if((phase==Phase.RUNNING||phase==Phase.PAUSED)&&(delivered>=mission.target||count(Status.LOST)>0||health<=0||fuel<=0||time<=0))return false;
        for(int i=0;i<N;i++)if(Math.abs(rx[i])>100000||Math.abs(ry[i])>100000||Math.abs(ox[i])>100000||Math.abs(oy[i])>100000)return false;
        for(Person p:people)if(Math.abs(p.x)>100000||Math.abs(p.y)>100000||p.grip>32||p.grip< -1)return false;
        return true;
    }
}

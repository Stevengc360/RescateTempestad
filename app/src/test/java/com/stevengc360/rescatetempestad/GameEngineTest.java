package com.stevengc360.rescatetempestad;

import org.junit.Test;
import java.io.*;
import java.util.*;
import java.util.zip.CRC32;
import static org.junit.Assert.*;

public class GameEngineTest {
    static double steer(double target,double pos,double vel){return GameEngine.clamp(((target-pos)*1.8-vel)*.035,-1,1);}
    static int flyMission(GameEngine e,int limit){
        for(int i=0;i<limit&&e.phase==GameEngine.Phase.RUNNING;i++){
            GameEngine.Person goal=null;for(GameEngine.Person p:e.people)if(p.status==GameEngine.Status.WAITING){goal=p;break;}
            double tx=180,ty=250;int winch=-1;
            if(e.count(GameEngine.Status.HANGING)>0){tx=e.x;ty=200;}
            else if(e.cabin>0||goal==null){tx=180;ty=Math.abs(e.x-180)<110?475:200;}
            else{tx=goal.x;boolean near=Math.abs(e.x-tx)<115;
                if(near&&e.rope>225){tx=e.x+GameEngine.clamp(goal.x-e.rx[13],-90,90)*.65;ty=e.y+GameEngine.clamp(goal.y-e.ry[13],-50,50)*.65;}else ty=near?e.ground(goal.x)-235:185;
                winch=near?1:-1;}
            e.tick(GameEngine.STEP,steer(tx,e.x,e.vx),steer(ty,e.y,e.vy),winch);
        }
        return e.delivered;
    }
    @Test public void initialState(){GameEngine e=new GameEngine(0);assertEquals(1,e.people.size());assertTrue(e.finite());assertEquals(100,e.health,0);}
    @Test public void clampsMission(){assertEquals(0,new GameEngine(-3).index);assertEquals(5,new GameEngine(99).index);}
    @Test public void pauseFreezes(){GameEngine e=new GameEngine(0);e.pause();e.tick(.1,1,1,1);assertEquals(240,e.time,0);assertEquals(180,e.x,0);}
    @Test public void resumeMoves(){GameEngine e=new GameEngine(0);e.pause();e.resume();e.tick(GameEngine.STEP,1,0,0);assertTrue(e.x>180);}
    @Test public void ignoresInvalidTime(){GameEngine e=new GameEngine(0);e.tick(Double.NaN,1,1,1);e.tick(-1,1,1,1);assertEquals(0,e.elapsed,0);}
    @Test public void sanitizesControls(){GameEngine e=new GameEngine(0);e.tick(GameEngine.STEP,Double.NaN,Double.POSITIVE_INFINITY,100);assertTrue(e.finite());}
    @Test public void limitsStep(){GameEngine e=new GameEngine(0);e.tick(20,0,0,0);assertEquals(1.0/30,e.elapsed,1e-9);}
    @Test public void ropeBounds(){GameEngine e=new GameEngine(0);for(int i=0;i<2000;i++)e.tick(GameEngine.STEP,0,0,1);assertEquals(245,e.rope,0);for(int i=0;i<2000;i++)e.tick(GameEngine.STEP,0,0,-1);assertEquals(65,e.rope,0);}
    @Test public void timeFailure(){GameEngine e=new GameEngine(0);e.time=.001;e.tick(GameEngine.STEP,0,0,0);assertEquals(GameEngine.Phase.LOST,e.phase);}
    @Test public void fuelFailure(){GameEngine e=new GameEngine(0);e.fuel=.00001;e.tick(GameEngine.STEP,0,0,0);assertEquals(GameEngine.Phase.LOST,e.phase);}
    @Test public void healthFailure(){GameEngine e=new GameEngine(0);e.health=0;e.tick(GameEngine.STEP,0,0,0);assertEquals(GameEngine.Phase.LOST,e.phase);}
    @Test public void capacityMatchesPeople(){GameEngine e=new GameEngine(5);assertEquals(6,e.people.size());assertEquals(0,e.count(GameEngine.Status.SAVED));}
    @Test public void terminalDoesNotAdvance(){GameEngine e=new GameEngine(0);e.phase=GameEngine.Phase.LOST;e.tick(GameEngine.STEP,1,1,1);assertEquals(0,e.elapsed,0);}
    @Test public void firstMissionThroughControls(){GameEngine e=new GameEngine(0);flyMission(e,18000);assertEquals(e.failure,GameEngine.Phase.WON,e.phase);}
    @Test public void allSixMissionsCompleteThroughControls(){
        for(int m=0;m<6;m++){GameEngine e=new GameEngine(m);flyMission(e,30000);assertEquals("Mission "+(m+1)+": "+e.failure,GameEngine.Phase.WON,e.phase);assertEquals(e.mission.target,e.delivered);}
    }
    @Test public void continuationAfterEveryRescueStage() throws Exception {
        GameEngine e=new GameEngine(0);boolean hanging=false,aboard=false;
        for(int i=0;i<18000&&e.phase==GameEngine.Phase.RUNNING;i++){
            flyMission(e,1);
            if(e.count(GameEngine.Status.HANGING)>0&&!hanging){e=GameEngine.restore(e.snapshot());hanging=true;}
            if(e.cabin>0&&!aboard){e=GameEngine.restore(e.snapshot());aboard=true;}
        }
        assertTrue(hanging);assertTrue(aboard);assertEquals(GameEngine.Phase.WON,e.phase);
    }
    @Test public void snapshotRoundtrip() throws Exception {GameEngine e=new GameEngine(3);for(int i=0;i<300;i++)e.tick(GameEngine.STEP,.2,-.1,0);assertArrayEquals(e.snapshot(),GameEngine.restore(e.snapshot()).snapshot());}
    @Test public void deterministicContinuationAfterRestore() throws Exception {GameEngine a=new GameEngine(4);for(int i=0;i<120;i++)a.tick(GameEngine.STEP,.2,-.3,1);GameEngine b=GameEngine.restore(a.snapshot());for(int i=0;i<700;i++){a.tick(GameEngine.STEP,-.1,.1,-1);b.tick(GameEngine.STEP,-.1,.1,-1);}assertArrayEquals(a.snapshot(),b.snapshot());}
    @Test public void pausedSnapshotRetainsPause() throws Exception {GameEngine e=new GameEngine(0);e.pause();assertEquals(GameEngine.Phase.PAUSED,GameEngine.restore(e.snapshot()).phase);}
    @Test public void damageToEveryByteRejected() throws Exception {byte[] good=new GameEngine(5).snapshot();for(int i=0;i<good.length;i++){byte[] bad=good.clone();bad[i]^=1;try{GameEngine.restore(bad);fail("Accepted corruption at "+i);}catch(IOException expected){}}}
    @Test public void allTruncationsRejected() throws Exception {byte[] good=new GameEngine(0).snapshot();for(int i=0;i<good.length;i++){try{GameEngine.restore(Arrays.copyOf(good,i));fail("Accepted truncation at "+i);}catch(IOException expected){}}}
    @Test public void rejectsNullEmptyHuge(){for(byte[] v:new byte[][]{null,new byte[0],new byte[16385]}){try{GameEngine.restore(v);fail();}catch(IOException expected){}}}
    static byte[] checksum(byte[] b) throws Exception {CRC32 crc=new CRC32();crc.update(b,0,b.length-8);ByteArrayOutputStream tail=new ByteArrayOutputStream();new DataOutputStream(tail).writeLong(crc.getValue());System.arraycopy(tail.toByteArray(),0,b,b.length-8,8);return b;}
    @Test public void rejectsUnknownSchemaEvenWithValidCRC() throws Exception {byte[] b=new GameEngine(0).snapshot();b[7]=5;try{GameEngine.restore(checksum(b));fail();}catch(IOException expected){}}
    @Test public void rejectsInvalidMissionEvenWithValidCRC() throws Exception {byte[] b=new GameEngine(0).snapshot();b[11]=99;try{GameEngine.restore(checksum(b));fail();}catch(IOException expected){}}
    @Test public void rejectsNonFiniteState() throws Exception {GameEngine e=new GameEngine(0);e.x=Double.NaN;try{GameEngine.restore(e.snapshot());fail();}catch(IOException expected){}}
    @Test public void rejectsInconsistentCounts() throws Exception {GameEngine e=new GameEngine(0);e.cabin=3;try{GameEngine.restore(e.snapshot());fail();}catch(IOException expected){}}
    @Test public void rejectsOutOfBoundsHealth() throws Exception {GameEngine e=new GameEngine(0);e.health=101;try{GameEngine.restore(e.snapshot());fail();}catch(IOException expected){}}
    @Test public void allMissionsSurviveRepeatedCheckpointRestoration() throws Exception {
        for(int m=0;m<6;m++){
            GameEngine e=new GameEngine(m);
            for(int i=0;i<30000&&e.phase==GameEngine.Phase.RUNNING;i++){
                flyMission(e,1);
                if(i%73==0&&e.phase==GameEngine.Phase.RUNNING){e.pause();e=GameEngine.restore(e.snapshot());e.resume();}
            }
            assertEquals("Checkpointed mission "+m,GameEngine.Phase.WON,e.phase);
        }
    }
    @Test public void rejectsImpossibleVictory() throws Exception {GameEngine e=new GameEngine(0);e.phase=GameEngine.Phase.WON;try{GameEngine.restore(e.snapshot());fail();}catch(IOException expected){}}
    @Test public void rejectsInvalidTornado() throws Exception {GameEngine e=new GameEngine(0);e.tornadoX=Double.MAX_VALUE;try{GameEngine.restore(e.snapshot());fail();}catch(IOException expected){}}
    @Test public void rejectsActiveLostPerson() throws Exception {GameEngine e=new GameEngine(0);e.people.get(0).status=GameEngine.Status.LOST;try{GameEngine.restore(e.snapshot());fail();}catch(IOException expected){}}
    @Test public void validVictoryRestores() throws Exception {GameEngine e=new GameEngine(0);flyMission(e,18000);GameEngine f=GameEngine.restore(e.snapshot());assertEquals(GameEngine.Phase.WON,f.phase);assertEquals(1,f.delivered);}
    @Test public void validLossRestores() throws Exception {GameEngine e=new GameEngine(0);e.health=0;e.tick(GameEngine.STEP,0,0,0);assertEquals(GameEngine.Phase.LOST,GameEngine.restore(e.snapshot()).phase);}
    @Test public void randomizedStressRemainsFinite() throws Exception {Random r=new Random(42);for(int m=0;m<6;m++){GameEngine e=new GameEngine(m);for(int i=0;i<36000;i++){if(e.phase!=GameEngine.Phase.RUNNING)e=new GameEngine(m);e.tick(GameEngine.STEP,r.nextDouble()*2-1,r.nextDouble()*2-1,r.nextInt(3)-1);assertTrue("mission "+m+" step "+i,e.finite());if(i%997==0)e=GameEngine.restore(e.snapshot());}}}
    @Test public void starsBounded(){GameEngine e=new GameEngine(0);assertEquals(0,e.stars());e.phase=GameEngine.Phase.WON;assertEquals(3,e.stars());e.health=20;e.time=1;assertEquals(1,e.stars());}
}

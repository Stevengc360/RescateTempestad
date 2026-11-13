package com.stevengc360.rescatetempestad;

import org.junit.Test;
import java.io.IOException;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

public class EngineRegressionTest {
    private static GameEngine atBase(){GameEngine e=new GameEngine(0);e.x=180;e.y=475;return e;}
    private static void set(GameEngine e,String name,double value)throws Exception{Field f=GameEngine.class.getDeclaredField(name);f.setAccessible(true);f.setDouble(e,value);}
    @Test public void fatalHealthCannotBeRepairedAtBase(){GameEngine e=atBase();e.health=0;e.tick(GameEngine.STEP,0,0,0);assertEquals(GameEngine.Phase.LOST,e.phase);assertEquals(0,e.health,0);}
    @Test public void exhaustedFuelCannotBeRecoveredAtBase(){GameEngine e=atBase();e.fuel=.00001;e.tick(GameEngine.STEP,0,0,0);assertEquals(GameEngine.Phase.LOST,e.phase);assertEquals(0,e.fuel,0);}
    @Test public void deadlineCannotBeBeatenByDeliveryInSameStep()throws Exception{GameEngine e=atBase();e.people.get(0).status=GameEngine.Status.ABOARD;e.cabin=1;set(e,"dockTime",.7);e.time=.001;e.tick(GameEngine.STEP,0,0,0);assertEquals(GameEngine.Phase.LOST,e.phase);assertEquals(0,e.delivered);}
    @Test public void lostSurvivorPrecedesDelivery()throws Exception{GameEngine e=new GameEngine(1);e.x=180;e.y=475;e.people.get(0).status=GameEngine.Status.ABOARD;e.cabin=1;e.people.get(1).status=GameEngine.Status.LOST;set(e,"dockTime",.7);e.tick(GameEngine.STEP,0,0,0);assertEquals(GameEngine.Phase.LOST,e.phase);assertEquals(0,e.delivered);}
    @Test public void leftBoundaryStopsOutwardVelocity(){GameEngine e=new GameEngine(0);e.x=60;e.vx=-200;e.tick(GameEngine.STEP,0,0,0);assertEquals(60,e.x,0);assertEquals(0,e.vx,0);}
    @Test public void rightBoundaryStopsOutwardVelocity(){GameEngine e=new GameEngine(0);e.x=e.mission.width-60;e.vx=200;e.tick(GameEngine.STEP,0,0,0);assertEquals(e.mission.width-60,e.x,0);assertEquals(0,e.vx,0);}
    @Test public void canMoveInwardAfterBoundary(){GameEngine e=new GameEngine(0);e.x=60;e.vx=-200;e.tick(GameEngine.STEP,0,0,0);e.tick(GameEngine.STEP,1,0,0);assertTrue(e.x>60);}
    @Test public void invalidStateCannotBeSerialized()throws Exception{GameEngine e=new GameEngine(0);e.health=101;try{e.snapshot();fail("Invalid state serialized");}catch(IOException expected){}}
    @Test public void brokenRopeCannotCollectWaitingPersonInBreakingStep()throws Exception{
        GameEngine e=new GameEngine(2);e.rope=65;
        for(int i=0;i<GameEngine.N;i++){e.rx[i]=e.x;e.ry[i]=e.y+20+65.0*i/(GameEngine.N-1);}
        Field fx=GameEngine.class.getDeclaredField("ox"),fy=GameEngine.class.getDeclaredField("oy");fx.setAccessible(true);fy.setAccessible(true);System.arraycopy(e.rx,0,(double[])fx.get(e),0,GameEngine.N);System.arraycopy(e.ry,0,(double[])fy.get(e),0,GameEngine.N);
        GameEngine.Person p=e.people.get(0);p.x=e.rx[13];p.y=e.ry[13];e.tension=1.5;set(e,"overload",.61);e.tick(GameEngine.STEP,0,0,0);assertTrue(e.broken);assertEquals(GameEngine.Status.WAITING,p.status);assertEquals(0,e.cabin);
    }
    @Test public void ropeBreakDropsHangingSurvivorAndSpareRopeRecovers()throws Exception{
        GameEngine e=new GameEngine(2);e.rope=65;
        for(int i=0;i<GameEngine.N;i++){e.rx[i]=e.x;e.ry[i]=e.y+20+65.0*i/(GameEngine.N-1);}
        Field fx=GameEngine.class.getDeclaredField("ox"),fy=GameEngine.class.getDeclaredField("oy");fx.setAccessible(true);fy.setAccessible(true);System.arraycopy(e.rx,0,(double[])fx.get(e),0,GameEngine.N);System.arraycopy(e.ry,0,(double[])fy.get(e),0,GameEngine.N);
        GameEngine.Person p=e.people.get(0);p.x=e.rx[13];p.y=e.ry[13];p.status=GameEngine.Status.HANGING;e.tension=1.5;set(e,"overload",.61);
        e.tick(GameEngine.STEP,0,0,0);
        assertTrue(e.broken);assertEquals(GameEngine.Status.WAITING,p.status);assertEquals(GameEngine.Phase.RUNNING,e.phase);assertEquals(e.ground(p.x)-18,p.y,0);
        for(int i=0;i<200;i++)e.tick(GameEngine.STEP,0,0,1);
        assertFalse(e.broken);assertTrue(e.rope>=90);assertEquals(GameEngine.Phase.RUNNING,e.phase);
    }
    @Test public void allMissionsCompleteWithRepeatedRestores()throws Exception{
        for(int m=0;m<GameEngine.MISSIONS.length;m++){GameEngine e=new GameEngine(m);for(int i=0;i<30000&&e.phase==GameEngine.Phase.RUNNING;i++){GameEngineTest.flyMission(e,1);if(i%73==0&&e.phase==GameEngine.Phase.RUNNING){e.pause();e=GameEngine.restore(e.snapshot());e.resume();}}assertEquals("mission "+m+": "+e.failure,GameEngine.Phase.WON,e.phase);}
    }
}

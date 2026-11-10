package com.stevengc360.rescatetempestad;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;
import java.io.*;

final class GameStore {
    private final SharedPreferences prefs;
    private final AtomicFile file;
    String warning="";
    GameStore(Context c){prefs=c.getSharedPreferences("campaign_v1",Context.MODE_PRIVATE);file=new AtomicFile(new File(c.getFilesDir(),"active-mission-v1.bin"));}
    private int integer(String key,int fallback){try{return prefs.getInt(key,fallback);}catch(ClassCastException bad){return fallback;}}
    private boolean flag(String key,boolean fallback){try{return prefs.getBoolean(key,fallback);}catch(ClassCastException bad){return fallback;}}
    int unlocked(){return Math.max(0,Math.min(5,integer("unlocked",0)));}
    int stars(int i){return Math.max(0,Math.min(3,integer("stars_"+i,0)));}
    void complete(GameEngine e){
        if(e==null||e.phase!=GameEngine.Phase.WON||e.delivered!=e.mission.target)return;
        try{e.snapshot();}catch(IOException|RuntimeException invalid){warning="No se pudo validar el resultado de la misión.";return;}
        prefs.edit().putInt("unlocked",Math.max(unlocked(),Math.min(5,e.index+1))).putInt("stars_"+e.index,Math.max(stars(e.index),e.stars())).apply();clear();warning="";
    }
    boolean calm(){return flag("calm",false);}
    boolean sound(){return flag("sound",true);}
    void toggleCalm(){prefs.edit().putBoolean("calm",!calm()).apply();}
    void toggleSound(){prefs.edit().putBoolean("sound",!sound()).apply();}
    void clear(){file.delete();}
    boolean save(GameEngine e){
        if(e==null)return true;
        if(e.phase==GameEngine.Phase.WON||e.phase==GameEngine.Phase.LOST){clear();warning="";return true;}
        FileOutputStream out=null;
        try{byte[] bytes=e.snapshot();out=file.startWrite();out.write(bytes);file.finishWrite(out);warning="";return true;}
        catch(IOException|RuntimeException error){if(out!=null)file.failWrite(out);warning="No se pudo guardar. Libera espacio y vuelve a intentarlo.";return false;}
    }
    GameEngine load(){
        try(FileInputStream in=file.openRead()){
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();byte[] block=new byte[1024];int n;
            while((n=in.read(block))!=-1){if(buffer.size()+n>16384)throw new IOException("Archivo demasiado grande");buffer.write(block,0,n);}
            GameEngine e=GameEngine.restore(buffer.toByteArray());
            if(e.phase==GameEngine.Phase.WON||e.phase==GameEngine.Phase.LOST){clear();return null;}
            e.pause();return e;
        }catch(FileNotFoundException absent){return null;}
        catch(IOException|RuntimeException bad){clear();warning="Guardado dañado o incompatible descartado; campaña conservada.";return null;}
    }
}

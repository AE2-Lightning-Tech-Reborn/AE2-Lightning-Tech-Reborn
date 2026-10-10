package com.moakiee.ae2lt.integration.useless;
import java.lang.reflect.Method;
final class UselessModReflection {
    static Object call(Object owner,String name,Object... args) {
        try {
            Class<?> type=owner instanceof String className?Class.forName(className):owner.getClass();
            for(Method method:type.getMethods()) {
                if(!method.getName().equals(name) || method.getParameterCount()!=args.length) continue;
                var types=method.getParameterTypes();boolean fits=true;
                for(int i=0;i<args.length;i++) if(args[i]!=null && !types[i].isInstance(args[i])) {fits=false;break;}
                if(fits) return method.invoke(owner instanceof String?null:owner,args);
            }
            throw new NoSuchMethodException(type.getName()+"."+name);
        } catch(ReflectiveOperationException failure) {throw new IllegalStateException("Optional Useless Mod API is unavailable",failure);}
    }
    static boolean is(Object value,String name) {
        try {return Class.forName(name).isInstance(value);} catch(ClassNotFoundException ignored){return false;}
    }
    private UselessModReflection() {}
}

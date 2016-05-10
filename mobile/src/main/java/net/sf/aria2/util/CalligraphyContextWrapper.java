package net.sf.aria2.util;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.support.annotation.NonNull;
import android.support.v7.widget.AppCompatCheckBox;
import android.support.v7.widget.AppCompatCheckedTextView;
import android.support.v7.widget.AppCompatEditText;
import android.support.v7.widget.AppCompatRadioButton;
import android.support.v7.widget.AppCompatSpinner;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.annotation.TargetApi;
import android.os.Build;
import android.view.ViewGroup;
import net.sf.aria2.R;
import org.xmlpull.v1.XmlPullParser;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class CalligraphyContextWrapper extends ContextWrapper {
    private CalligraphyLayoutInflater mInflater;

    public static ContextWrapper wrap(Context base) {
        return new CalligraphyContextWrapper(base);
    }

    public static View onActivityCreateView(Activity activity, View parent, View view, String name, Context context, AttributeSet attr) {
        return get(activity).onActivityCreateView(parent, view, name, context, attr);
    }

    static CalligraphyActivityFactory get(Activity activity) {
        if (!(activity.getLayoutInflater() instanceof CalligraphyLayoutInflater)) {
            throw new RuntimeException("This activity does not wrap the Base Context! See CalligraphyContextWrapper.wrap(Context)");
        }
        return (CalligraphyActivityFactory) activity.getLayoutInflater();
    }

    public CalligraphyContextWrapper(Context base) {
        super(base);
    }

    @Override
    public Object getSystemService(String name) {
        if (LAYOUT_INFLATER_SERVICE.equals(name)) {
            if (mInflater == null) {
                mInflater = new CalligraphyLayoutInflater(LayoutInflater.from(getBaseContext()), this);
            }
            return mInflater;
        }
        return super.getSystemService(name);
    }
}

interface CalligraphyActivityFactory {
    View onActivityCreateView(View parent, View view, String name, Context context, AttributeSet attrs);
}

class ReflectionUtils {
    static Field getField(Class clazz, String fieldName) {
        try {
            final Field f = clazz.getDeclaredField(fieldName);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException ignored) {
        }
        return null;
    }
    static Object getValue(Field field, Object obj) {
        try {
            return field.get(obj);
        } catch (IllegalAccessException ignored) {
        }
        return null;
    }
    static void setValue(Field field, Object obj, Object value) {
        try {
            field.set(obj, value);
        } catch (IllegalAccessException ignored) {
        }
    }
    static Method getMethod(Class clazz, String methodName) {
        final Method[] methods = clazz.getMethods();
        for (Method method : methods) {
            if (method.getName().equals(methodName)) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }
    static void invokeMethod(Object object, Method method, Object... args) {
        try {
            if (method == null) return;
            method.invoke(object, args);
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            ignored.printStackTrace();
        }
    }
}

class CalligraphyLayoutInflater extends LayoutInflater implements CalligraphyActivityFactory {
    private static final String[] sClassPrefixList = {
            "android.widget."
    };

    private final CalligraphyFactory mCalligraphyFactory;

    // Reflection Hax
    private boolean mSetPrivateFactory = false;
    private Field mConstructorArgs = null;

    protected CalligraphyLayoutInflater(Context context) {
        super(context);
        mCalligraphyFactory = new CalligraphyFactory();
        setUpLayoutFactories();
    }

    protected CalligraphyLayoutInflater(LayoutInflater original, Context newContext) {
        super(original, newContext);
        mCalligraphyFactory = new CalligraphyFactory();
        setUpLayoutFactories();
    }
    @Override
    public LayoutInflater cloneInContext(Context newContext) {
        return new CalligraphyLayoutInflater(this, newContext);
    }

    @Override
    public View inflate(@NonNull XmlPullParser parser, ViewGroup root, boolean attachToRoot) {
        setPrivateFactoryInternal();
        return super.inflate(parser, root, attachToRoot);
    }

    private void setUpLayoutFactories() {
        // If we are HC+ we get and set Factory2 otherwise we just wrap Factory1
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            if (getFactory2() != null && !(getFactory2() instanceof WrapperFactory2)) {
            // Sets both Factory/Factory2
                setFactory2(getFactory2());
            }
        }
        // We can do this as setFactory2 is used for both methods.
        if (getFactory() != null && !(getFactory() instanceof WrapperFactory)) {
            setFactory(getFactory());
        }
    }
    @Override
    public void setFactory(Factory factory) {
        // Only set our factory and wrap calls to the Factory trying to be set!
        if (!(factory instanceof WrapperFactory)) {
            super.setFactory(new WrapperFactory(factory, this, mCalligraphyFactory));
        } else {
            super.setFactory(factory);
        }
    }
    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void setFactory2(Factory2 factory2) {
        // Only set our factory and wrap calls to the Factory2 trying to be set!
        if (!(factory2 instanceof WrapperFactory2)) {
            super.setFactory2(new WrapperFactory2(factory2, mCalligraphyFactory));
        } else {
            super.setFactory2(factory2);
        }
    }
    private void setPrivateFactoryInternal() {
        // Already tried to set the factory.
        if (mSetPrivateFactory) return;

        // Reflection (Or Old Device) skip.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) return;

        // Skip if not attached to an activity.
        if (!(getContext() instanceof Factory2)) {
            mSetPrivateFactory = true;
            return;
        }
        final Method setPrivateFactoryMethod = ReflectionUtils
                .getMethod(LayoutInflater.class, "setPrivateFactory");
        if (setPrivateFactoryMethod != null) {
            ReflectionUtils.invokeMethod(this,
                    setPrivateFactoryMethod,
                    new PrivateWrapperFactory2((Factory2) getContext(), this, mCalligraphyFactory));
        }
        mSetPrivateFactory = true;
    }
    // ===
    // LayoutInflater ViewCreators
    // Works in order of inflation
    // ===
    /**
     * The Activity onCreateView (PrivateFactory) is the third port of call for LayoutInflation.
     * We opted to manual injection over aggressive reflection, this should be less fragile.
     */
    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public View onActivityCreateView(View parent, View view, String name, Context context, AttributeSet attrs) {
        return mCalligraphyFactory.onViewCreated(createCustomViewInternal(parent, view, name, context, attrs), context, attrs);
    }

    /**
     * The LayoutInflater onCreateView is the fourth port of call for LayoutInflation.
     * BUT only for none CustomViews.
     */
    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    protected View onCreateView(View parent, String name, AttributeSet attrs) throws ClassNotFoundException {
        return mCalligraphyFactory.onViewCreated(super.onCreateView(parent, name, attrs),
                getContext(), attrs);
    }

    /**
     * The LayoutInflater onCreateView is the fourth port of call for LayoutInflation.
     * BUT only for none CustomViews.
     * Basically if this method doesn't inflate the View nothing probably will.
     */
    @Override
    protected View onCreateView(String name, AttributeSet attrs) throws ClassNotFoundException {
        // This mimics the {@code PhoneLayoutInflater} in the way it tries to inflate the base
        // classes, if this fails its pretty certain the app will fail at this point.
        View view = null;
        for (String prefix : sClassPrefixList) {
            try {
                view = createView(name, prefix, attrs);
            } catch (ClassNotFoundException ignored) {
            }
        }
        // In this case we want to let the base class take a crack
        // at it.
        if (view == null) view = super.onCreateView(name, attrs);
        return mCalligraphyFactory.onViewCreated(view, view.getContext(), attrs);
    }

    private View createCustomViewInternal(View parent, View view, String name, Context context, AttributeSet attrs) {
        // I by no means advise anyone to do this normally, but Google have locked down access to
        // the createView() method, so we never get a callback with attributes at the end of the
        // createViewFromTag chain (which would solve all this unnecessary rubbish).
        // We at the very least try to optimise this as much as possible.
        // We only call for customViews (As they are the ones that never go through onCreateView(...)).
        // We also maintain the Field reference and make it accessible which will make a pretty
        // significant difference to performance on Android 4.0+.
        // If CustomViewCreation is off skip this.
        // TODO check if it works everywhere
        if (true) return view;

        if (view == null && name.indexOf('.') > -1) {
            if (mConstructorArgs == null)
                mConstructorArgs = ReflectionUtils.getField(LayoutInflater.class, "mConstructorArgs");
            final Object[] mConstructorArgsArr = (Object[]) ReflectionUtils.getValue(mConstructorArgs, this);
            final Object lastContext = mConstructorArgsArr[0];
            mConstructorArgsArr[0] = parent != null ? parent.getContext() : context;
            ReflectionUtils.setValue(mConstructorArgs, this, mConstructorArgsArr);
            try {
                view = createView(name, null, attrs);
            } catch (ClassNotFoundException ignored) {
            } finally {
                mConstructorArgsArr[0] = lastContext;
                ReflectionUtils.setValue(mConstructorArgs, this, mConstructorArgsArr);
            }
        }
        return view;
    }

    private static class WrapperFactory implements Factory {
        private final Factory mFactory;
        private final CalligraphyLayoutInflater mInflater;
        private final CalligraphyFactory mCalligraphyFactory;
        public WrapperFactory(Factory factory, CalligraphyLayoutInflater inflater, CalligraphyFactory calligraphyFactory) {
            mFactory = factory;
            mInflater = inflater;
            mCalligraphyFactory = calligraphyFactory;
        }
        @Override
        public View onCreateView(String name, Context context, AttributeSet attrs) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
                return mCalligraphyFactory.onViewCreated(
                        mInflater.createCustomViewInternal(
                                null, mFactory.onCreateView(name, context, attrs), name, context, attrs
                        ),
                        context, attrs
                );
            }
            return mCalligraphyFactory.onViewCreated(
                    mFactory.onCreateView(name, context, attrs),
                    context, attrs
            );
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private static class WrapperFactory2 implements Factory2 {
        protected final Factory2 mFactory2;
        protected final CalligraphyFactory mCalligraphyFactory;
        public WrapperFactory2(Factory2 factory2, CalligraphyFactory calligraphyFactory) {
            mFactory2 = factory2;
            mCalligraphyFactory = calligraphyFactory;
        }
        @Override
        public View onCreateView(String name, Context context, AttributeSet attrs) {
            return mCalligraphyFactory.onViewCreated(
                    mFactory2.onCreateView(name, context, attrs),
                    context, attrs);
        }
        @Override
        public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
            return mCalligraphyFactory.onViewCreated(
                    mFactory2.onCreateView(parent, name, context, attrs),
                    context, attrs);
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private static class PrivateWrapperFactory2 extends WrapperFactory2 {
        private final CalligraphyLayoutInflater mInflater;
        public PrivateWrapperFactory2(Factory2 factory2, CalligraphyLayoutInflater inflater, CalligraphyFactory calligraphyFactory) {
            super(factory2, calligraphyFactory);
            mInflater = inflater;
        }
        @Override
        public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
            return mCalligraphyFactory.onViewCreated(
                    mInflater.createCustomViewInternal(parent,
                            mFactory2.onCreateView(parent, name, context, attrs),
                            name, context, attrs
                    ),
                    context, attrs
            );
        }
    }
}

class CalligraphyFactory {
    public View onViewCreated(View view, Context context, AttributeSet attrs) {
        if (view != null && view.getTag(R.id.customfactory_tag_id) != Boolean.TRUE) {

            switch (view.getClass().getName()) {
                case "android.widget.EditText":
                    view = new AppCompatEditText(context, attrs);
                    break;
                case "android.widget.Spinner":
                    view = new AppCompatSpinner(context, attrs);
                    break;
                case "android.widget.CheckBox":
                    view = new AppCompatCheckBox(context, attrs);
                    break;
                case "android.widget.RadioButton":
                    view = new AppCompatRadioButton(context, attrs);
                    break;
                case "android.widget.CheckedTextView":
                    view = new AppCompatCheckedTextView(context, attrs);
                    break;
            }

            view.setTag(R.id.customfactory_tag_id, Boolean.TRUE);
        }
        return view;
    }
}
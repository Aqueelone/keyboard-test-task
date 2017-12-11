package test;

import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.platform.unix.X11;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.NativeLongByReference;
import com.sun.jna.ptr.PointerByReference;
import org.jnativehook.GlobalScreen;
import org.jnativehook.NativeHookException;
import org.jnativehook.keyboard.NativeKeyEvent;
import org.jnativehook.keyboard.NativeKeyListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;


public class Keylogger implements NativeKeyListener {
    private static Logger log = LoggerFactory.getLogger(Keylogger.class);

    public void nativeKeyPressed(NativeKeyEvent e) {
        log.info("Key Pressed: " + NativeKeyEvent.getKeyText(e.getKeyCode()));

        if (e.getKeyCode() == NativeKeyEvent.VC_ESCAPE) {
            try {
                GlobalScreen.unregisterNativeHook();
            } catch (NativeHookException e1) {
                log.error("Error ", e1);
            }
        }
    }

    public void nativeKeyReleased(NativeKeyEvent e) {
        log.info("Key Released: " + NativeKeyEvent.getKeyText(e.getKeyCode()));
    }

    public void nativeKeyTyped(NativeKeyEvent e) {
        log.info("Key Typed: " + e.getKeyText(e.getKeyCode()) + " with char: " + e.getKeyChar() );
    }

    public static void setKbdNativeHook() {
        // Get the "org.jnativehook" logger and set the level
        java.util.logging.Logger jNativeLogger = java.util.logging.Logger.getLogger(GlobalScreen.class.getPackage().getName());
        jNativeLogger.setLevel(java.util.logging.Level.ALL);

        // Don't forget to disable the parent handlers.
        jNativeLogger.setUseParentHandlers(false);

        try {
            GlobalScreen.registerNativeHook();
        }
        catch (NativeHookException ex) {
            log.error("There was a problem registering the native hook.", ex);
            System.exit(1);
        }
        GlobalScreen.addNativeKeyListener(new Keylogger());

    }

    public static void printX11info(String pre, X.Window wnd) {
        try {
            System.out.println(pre + ".getTitle() = " + wnd.getTitle());
            System.out.println(pre + ".getWindowClass() = " + wnd.getWindowClass());
            System.out.println(pre + ".getPID() = " + wnd.getPID());
        } catch(X.X11Exception ex) {
            log.error("Can't get info for window. ", ex);
        }
    }

    /**
     * Returns the property value as a byte array.
     *
     * @param display X11 display
     * @param window X11 window
     * @param xa_prop_type property type
     * @param xa_prop_name property name
     * @return property value as a byte array
     */
    public static byte[] getX11Property(X11.Display display, X11.Window window, X11.Atom xa_prop_type, X11.Atom xa_prop_name)  {
        final int MAX_PROPERTY_VALUE_LEN = 4096;
        X11 x11 = X11.INSTANCE;
        String prop_name = x11.XGetAtomName(display, xa_prop_name);
        log.debug("getX11Property() for {}", prop_name);
        X11.AtomByReference xa_ret_type_ref = new X11.AtomByReference();
        IntByReference ret_format_ref = new IntByReference();
        NativeLongByReference ret_nitems_ref = new NativeLongByReference();
        NativeLongByReference ret_bytes_after_ref = new NativeLongByReference();
        PointerByReference ret_prop_ref = new PointerByReference();

        NativeLong long_offset = new NativeLong(0);
        NativeLong long_length = new NativeLong(MAX_PROPERTY_VALUE_LEN / 4);

        /* MAX_PROPERTY_VALUE_LEN / 4 explanation (XGetWindowProperty manpage):
         *
         * long_length = Specifies the length in 32-bit multiples of the
         *               data to be retrieved.
         */
        log.debug("getX11Property() getting property");
        int x11_window_prop = X11.Success + 1;
        try {
            x11_window_prop = x11.XGetWindowProperty(display, window, xa_prop_name, long_offset, long_length, false,
                    xa_prop_type, xa_ret_type_ref, ret_format_ref,
                    ret_nitems_ref, ret_bytes_after_ref, ret_prop_ref);
        } catch (Exception ex) {
            throw new Error(ex);
        } catch (Error ex2) {
            throw ex2;
        }
        if (x11_window_prop != X11.Success) {
            // TODO - custom error
            throw new Error("Cannot get " + prop_name + " property.");
        }

        log.debug("getX11Property() property getted");
        X11.Atom xa_ret_type = xa_ret_type_ref.getValue();
        Pointer ret_prop = ret_prop_ref.getValue();

//        if( xa_ret_type == null ){
//            //the specified property does not exist for the specified window
//            return null;
//        }
//
//        if( xa_ret_type == null ){
//            //the specified property does not exist for the specified window
//            return null;
//        }
//
//        if( xa_ret_type == null ){
//            //the specified property does not exist for the specified window
//            return null;
//        }

        if ((xa_ret_type == null) || xa_prop_type == null ||
                !xa_ret_type.toNative().equals(xa_prop_type.toNative())) {
            x11.XFree(ret_prop);
            // TODO cutom error
            throw new Error("Invalid type of " + prop_name + " property");
        }

        int ret_format = ret_format_ref.getValue();
        long ret_nitems = ret_nitems_ref.getValue().longValue();

        // null terminate the result to make string handling easier
        int nbytes;
        if (ret_format == 32)
            nbytes = Native.LONG_SIZE;
        else if (ret_format == 16)
            nbytes = Native.LONG_SIZE / 2;
        else if (ret_format == 8)
            nbytes = 1;
        else if (ret_format == 0)
            nbytes = 0;
        else
            // TODO custom error
            throw new Error("Invalid return format");
        int length = Math.min((int) ret_nitems * nbytes, MAX_PROPERTY_VALUE_LEN);

        byte[] ret = ret_prop.getByteArray(0, length);

        x11.XFree(ret_prop);
        return ret;
    }

    public static String getActiveWindowTitle() {
        // X11 instanse
        X11 x11 = X11.INSTANCE;

        /* Get X11 display */
        X11.Display display = x11.XOpenDisplay(null);
        if (display == null) {
            // TODO - custom error type
            throw new Error("Can't open display");
        }

        /* Get root X11 window */
        X11.Window rootWindow = x11.XDefaultRootWindow(display);

        /* Get active X11 window ID */
        String prop_name = "_NET_ACTIVE_WINDOW";
        X11.Atom xa_prop_type = X11.XA_WINDOW;
        log.debug("getActiveWindowTitle() - converting property");
        X11.Atom xa_prop_name = x11.XInternAtom(display, prop_name, false);
        log.debug("getActiveWindowTitle() - getting active window");
        byte[] windowsIdProp = getX11Property(display, rootWindow, xa_prop_type, xa_prop_name);
        Integer windowId = ((windowsIdProp[3] & 0xff) << 24)
                | ((windowsIdProp[2] & 0xff) << 16)
                | ((windowsIdProp[1] & 0xff) << 8)
                | ((windowsIdProp[0] & 0xff));

        log.debug("getActiveWindowTitle() - active window id is {}", windowId);
        /* Get active X11 window */
        X11.Window activeWindow;
        try {
            activeWindow = new X11.Window(windowId);
        } catch (Exception ex) {
            throw new Error(ex);
        }
        if (activeWindow == null) {
            throw new Error("Can't create X11.Window");
        }
        log.debug("getActiveWindowTitle() - getting active window title");
        xa_prop_name = x11.XInternAtom(display, "_NET_WM_NAME", false);
        byte[] activeWindowTitleProp;
        try {
            activeWindowTitleProp = getX11Property(display, activeWindow, X11.XA_STRING, xa_prop_name);
        } catch (Error ex) {
            try {
                activeWindowTitleProp = getX11Property(display, activeWindow, X11.XA_STRING, X11.XA_WM_NAME);
            } catch(Error ex2) {
                throw ex2;
            }
        }
        String activeWindowTitle = null;
        if (activeWindowTitleProp != null) {
            try {
                activeWindowTitle = new String(activeWindowTitleProp, "UTF8");
            } catch (UnsupportedEncodingException ex) {
                // TODO custom error
                throw new Error(ex);
            }
        }
        return activeWindowTitle;
    }

    public static void main(String[] args) {
        log.info("Starting application!");
        X11.INSTANCE.XSetErrorHandler((display, errorEvent) -> {
            log.error("X11 Error {}", errorEvent);
            return 0;
        });
        X.Display display = new X.Display();
        try {
            printX11info("root", display.getRootWindow());
            printX11info("wm", display.getWindowManagerInfo());
            try {
                System.out.println("otherActive.getTitle() = " + VMUtil.getActiveWindowName());
                System.out.println("myActive.getTitle() = " + getActiveWindowTitle());
                printX11info("active", display.getActiveWindow());
            } catch (Error | Exception ex) {
                log.error("X11 Exception", ex);
            } catch (Throwable t) {
                log.error("X11 Throwable", t);
            }
        } catch(X.X11Exception ex) {
            log.error("X11 error, ", ex);
        } catch (Error ex2) {
            log.error("Error occured ", ex2);
        }

        Thread x11EventThread = new Thread(new Runnable() {
            @Override
            public void run() {
                log.debug("Running event loop thread");
                XUtil x11 = XUtil.INSTANCE;
                log.debug("Getting X11 display");
                X11.Display display = x11.XOpenDisplay(null);
                log.debug("Getting Root window");
                X11.Window rootWindow = x11.XDefaultRootWindow(display);
                X11.WindowByReference currentFocus = new X11.WindowByReference();
                // TODO
                IntByReference revert = new IntByReference();
                log.debug("Getting input focus");
                x11.XGetInputFocus(display, currentFocus, revert);
                log.debug("Start event consuming");
                x11.XSelectInput(display, currentFocus.getValue(), new NativeLong(X11.KeyPressMask|X11.KeyReleaseMask|X11.FocusChangeMask));
                while (true) {
                    X11.XEvent event = new X11.XEvent();
                    x11.XNextEvent(display, event);
                    switch (event.type) {
                        case X11.FocusOut:
                            log.debug("Focus changed");
                            int oldFocus = currentFocus.getValue().intValue();
                            if (oldFocus != rootWindow.intValue()) {
                                x11.XSelectInput(display, currentFocus.getValue(), new NativeLong(0));
                            }
                            x11.XGetInputFocus(display, currentFocus, revert);
                            if (currentFocus.getValue().intValue() == X11.PointerRoot) {
                            }
                            x11.XSelectInput(display, currentFocus.getValue(), new NativeLong(X11.KeyPressMask|X11.KeyReleaseMask|X11.FocusChangeMask));
                            X11.XTextProperty name = new X11.XTextProperty();
                            x11.XGetWMName(display, currentFocus.getValue(), name);
                            log.info("Focus changed from {} to {} - {}", oldFocus, currentFocus.getValue().intValue(), name.value);
                            break;
                        case X11.KeyPress:
                            int keyCode = event.xkey.keycode;
                            log.debug("Got key {}!", keyCode);

                            byte[] buf = new byte[16];
                            break;
                        case X11.KeyRelease:
                            log.debug("Key released");
                    }
                }
//                x11.XDestroyWindow(display, window);
//                int status = x11.XCloseDisplay(display);
            }

        });
//        x11EventThread.start();
    }

}

interface XUtil extends X11 {
    XUtil INSTANCE = Native.loadLibrary("X11", XUtil.class);

    /**
     *
     * @param d Display* Specifies the connection to the X server
     * @param returnFocus    Window* Returns the focus window, PointerRoot, or None
     * @param revertToReturn int* Returns the current focus state (RevertToParent, RevertToPointerRoot, or RevertToNone)
     * @return
     */
    int XGetInputFocus(X11.Display d, X11.WindowByReference returnFocus, IntByReference revertToReturn);

    /**
     *
     * @param ev    XKeyEvent* - event struct
     * @param buf   char* buffer return
     * @param len   int bytes buffer
     * @param kSym  KeySym* keysym return
     * @param xc    XComposeStatus* status in out
     * @return
     */
    int XLookupString(X11.XKeyEvent ev, byte[] buf, int len, KeySym kSym, IntByReference xc);
//        extern int XLookupString(
//          XKeyEvent*          /* event_struct */,
//          char*               /* buffer_return */,
//          int                 /* bytes_buffer */,
//          KeySym*             /* keysym_return */,
//          XComposeStatus*     /* status_in_out */
//        );

}

class VMUtil {
    final static XUtil x11 = XUtil.INSTANCE;

    static String getActiveWindowName() {
        X11.Display display = x11.XOpenDisplay(null);
        X11.WindowByReference wnd = new X11.WindowByReference();
        IntByReference revert = new IntByReference();
        x11.XGetInputFocus(display, wnd, revert);
        X11.XTextProperty name = new X11.XTextProperty();
        x11.XGetWMName(display, wnd.getValue(), name);
        return name.value;
    }
}
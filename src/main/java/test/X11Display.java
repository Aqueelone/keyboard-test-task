package test;

import com.sun.jna.platform.unix.X11;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

public class X11Display {
    private static final X11 x11 = X11.INSTANCE;

    /**
     * Returns the window manager information as an window.
     *
     * @return window manager information as an window
     * @throws X.X11Exception thrown if X11 window errors occurred
     */
    public X11Window getWindowManagerInfo() throws X.X11Exception {
        X11Window rootWindow = getRootWindow();

        try {
            return rootWindow.getWindowProperty(X11.XA_WINDOW, "_NET_SUPPORTING_WM_CHECK");
        } catch (X.X11Exception e) {
            try {
                return rootWindow.getWindowProperty(X11.XA_CARDINAL, "_WIN_SUPPORTING_WM_CHECK");
            } catch (X.X11Exception e1) {
                throw new X.X11Exception("Cannot get window manager info properties. (_NET_SUPPORTING_WM_CHECK or _WIN_SUPPORTING_WM_CHECK)");
            }
        }
    }

    private X11Window getRootWindow() {
        throw new NotImplementedException();
    }

}

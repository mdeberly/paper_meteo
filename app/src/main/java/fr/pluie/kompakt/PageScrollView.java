package fr.pluie.kompakt;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.ScrollView;

/**
 * Zone qui défile verticalement. Peut défiler page par page, sans animation (un glissement vers
 * le haut avance d'un écran, vers le bas recule d'un écran) ; réglage actuel : défilement fluide
 * habituel dans les deux versions (voir {@code paged}).
 */
public class PageScrollView extends ScrollView {
    /** Défilement page par page désactivé : défilement fluide dans les deux versions. */
    private final boolean paged = false;
    private final int slop;
    private float downX, downY;

    public PageScrollView(Context c) {
        super(c);
        slop = ViewConfiguration.get(c).getScaledTouchSlop() * 2;
        setOverScrollMode(OVER_SCROLL_NEVER);
        if (paged) {
            setSmoothScrollingEnabled(false);
            setScrollbarFadingEnabled(false); // la barre reste visible : on voit où l'on est
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (!paged) return super.onInterceptTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                return false;
            case MotionEvent.ACTION_MOVE:
                float dy = e.getY() - downY, dx = e.getX() - downX;
                // Glissement vertical franc : la page s'en occupe ; sinon (toucher, glissement
                // horizontal), l'élément touché le reçoit.
                return Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx);
            default:
                return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!paged) return super.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                return true;
            case MotionEvent.ACTION_UP:
                float dy = e.getY() - downY, dx = e.getX() - downX;
                if (Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx)) page(dy < 0 ? 1 : -1);
                return true;
            default:
                return true; // pas de défilement pendant le geste
        }
    }

    /** Avance (1) ou recule (-1) d'un écran, en gardant une ligne de recouvrement. */
    void page(int dir) {
        if (getChildCount() == 0) return;
        int step = Math.max(1, getHeight() - getHeight() / 12);
        int max = Math.max(0, getChildAt(0).getHeight() - getHeight());
        scrollTo(0, Math.max(0, Math.min(max, getScrollY() + dir * step)));
    }
}

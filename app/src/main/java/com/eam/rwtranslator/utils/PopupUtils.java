package com.eam.rwtranslator.utils;

/**
 * @Author EAM霜星
 * @Date 2023/02/25 21:17
 */
import android.content.Context;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;

public class PopupUtils {
    /**
     * 显示弹出菜单（通过菜单资源ID）
     * @param context 上下文
     * @param menu 菜单资源ID
     * @param view 依附的视图
     * @param itemListener 菜单项点击监听
     */
    public static void showPopupMenu(Context context,int menu,View view,PopupMenu.OnMenuItemClickListener itemListener) {
        PopupMenu popupMenu = new PopupMenu(context, view);
        popupMenu.getMenuInflater().inflate(menu, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(itemListener);
        popupMenu.show();
    }
    /**
     * 显示弹出菜单（通过字符串数组）
     * @param context 上下文
     * @param menuTitles 菜单项标题数组
     * @param view 依附的视图
     * @param itemListener 菜单项点击监听
     */
    public static void showPopupMenu(Context context,String[] menuTitles,View view,PopupMenu.OnMenuItemClickListener itemListener) {
        PopupMenu popupMenu = new PopupMenu(context, view);
        Menu menu = popupMenu.getMenu();
        for (int i = 0; i < menuTitles.length; i++) {
            MenuItem menuItem = menu.add(Menu.NONE, i, i, menuTitles[i]);
            menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        }
        popupMenu.setOnMenuItemClickListener(itemListener);
        popupMenu.show();
    }
    // 预留扩展：如需自定义弹窗item布局，可扩展ViewHolder
    private static class ViewHolder {
        TextView select_engine;
        TextView select_seclang;
    }
}

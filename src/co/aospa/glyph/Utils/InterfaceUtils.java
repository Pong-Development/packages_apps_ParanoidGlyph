package co.aospa.glyph.Utils;

import static co.aospa.glyph.Constants.Constants.CONTEXT;

import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.widget.Toast;

public class InterfaceUtils {

    public static <T> void showToast(T msg) {
        Toast.makeText(CONTEXT, resolve(msg), Toast.LENGTH_SHORT).show();
    }

    private static String resolve(Object value) {
        return switch (value) {
            case null -> null;
            case Integer i -> CONTEXT.getString(i);
            case String s -> s;
            default -> value.toString();
        };
    }

    public static <T> void showDialog(Context ctx, T title, T message,
                                      T positiveText, Runnable onPositive,
                                      T negativeText, Runnable onNegative,
                                      T neutralText, Runnable onNeutral) {
        new AlertDialog.Builder(ctx)
                .setTitle(resolve(title))
                .setMessage(resolve(message))
                .setPositiveButton(resolve(positiveText), (dialog, which) -> {
                    if (onPositive != null) onPositive.run();
                })
                .setNeutralButton(resolve(neutralText), (dialog, which) -> {
                    if (onNeutral != null) onNeutral.run();
                })
                .setNegativeButton(resolve(negativeText), (dialog, which) -> {
                    if (onNegative != null) onNegative.run();
                })
                .show();
    }

    public static <T> void showDialog(Context ctx, T title, T message,
                                      T positiveText, Runnable onPositive,
                                      T negativeText, Runnable onNegative) {
        showDialog(ctx, title, message,
                positiveText, onPositive,
                negativeText, onNegative,
                (String) null, null);
    }

    public static <T> void showDialog(Context ctx, T title, View messageVew,
                                      T positiveText, Runnable onPositive,
                                      T negativeText, Runnable onNegative,
                                      T neutralText, Runnable onNeutral) {
        new AlertDialog.Builder(ctx)
                .setTitle(resolve(title))
                .setView(messageVew)
                .setPositiveButton(resolve(positiveText), (dialog, which) -> {
                    if (onPositive != null) onPositive.run();
                })
                .setNeutralButton(resolve(neutralText), (dialog, which) -> {
                    if (onNeutral != null) onNeutral.run();
                })
                .setNegativeButton(resolve(negativeText), (dialog, which) -> {
                    if (onNegative != null) onNegative.run();
                })
                .show();
    }

    public static <T> void showDialog(Context ctx, T title, View messageView,
                                      T positiveText, Runnable onPositive,
                                      T negativeText, Runnable onNegative) {
        showDialog(ctx, title, messageView,
                positiveText, onPositive,
                negativeText, onNegative,
                (String) null, null);
    }


}

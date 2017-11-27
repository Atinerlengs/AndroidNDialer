package com.freeme.incallui.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.Gravity;

public class FreemeImageButtonWithText extends LinearLayout {

    private ImageView imageViewbutton;

    private TextView textView;

    public FreemeImageButtonWithText(Context context, AttributeSet attrs) {
        super(context, attrs);
        // TODO Auto-generated constructor stub

        imageViewbutton = new ImageView(context, attrs);
        imageViewbutton.setBackground(null);

        imageViewbutton.setPadding(0, 0, 0, 10);

        textView = new TextView(context, attrs);
        textView.setBackground(null);

        //Horizontal center
        textView.setGravity(Gravity.CENTER_HORIZONTAL);

        //getContext().getResources().getDimensionPixelSize(R.dimen.call_button_text_padding_top)
        //textView.setPadding(0, 0, 0, 40);
        setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);

        //setClickable(true);

        //setFocusable(true);

        //setBackground(null);

        setOrientation(LinearLayout.VERTICAL);

        addView(imageViewbutton);

        addView(textView);

    }

    public void setImage(int id) {
        imageViewbutton.setImageResource(id);
    }

    public void setText(int id) {
        textView.setText(id);
    }

    public void setText(String str) {
        textView.setText(str);
    }

    public CharSequence getText() {
        return textView.getText();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        textView.setEnabled(enabled);
        imageViewbutton.setEnabled(enabled);
    }
}

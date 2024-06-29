/**
 * 
 */
package com.kindred.gameday;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * @author Douglas Catchpole
 *
 */
public class InningPlay extends TextView {
	
	/**
	 * @param context
	 * @param attrs
	 */
	public InningPlay(Context context, AttributeSet attrs) {
		super(context, attrs);
		// TODO Auto-generated constructor stub
	}

	protected void onDraw(Canvas c) {
		int w = getWidth(), h = getHeight();
		Paint p = new Paint();
		Paint pLine = new Paint();
		pLine.setAntiAlias(true);
		pLine.setColor(0xff000000);
		p.setAntiAlias(true);
		if(getText().charAt(0) == '=') {
			p.setColor(0xffdddddd);
		} else if(getText().charAt(0) == '+') {
			p.setColor(0xff99ee99);
		} else {
			p.setColor(0xffffffff);
		}
		c.drawRoundRect(new RectF(4, 4, w-4, h), 3, 3, pLine);
		c.drawRoundRect(new RectF(5, 5, w-5, h-1), 3, 3, p);
		super.onDraw(c);
	}

}

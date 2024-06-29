/**
 * 
 */
package com.kindred.gameday;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Paint.Align;
import android.util.Log;
import android.view.View;

/**
 * @author Douglas Catchpole
 *
 */
public class PitchMap extends View {

	private Gameday mActivity;
	private String playDescription;
	private Paint text, strikeZone, description;
	
	
	public PitchMap(Context context) {
		super(context);
		mActivity = (Gameday)context;
		
		text = new Paint();
		text.setFakeBoldText(true);
		text.setColor(0xffffffff);
		text.setAntiAlias(true);
		text.setTextAlign(Align.CENTER);
		
		strikeZone = new Paint();
		strikeZone.setShadowLayer(10, 0, 0, 0x99ffffff);
		strikeZone.setColor(0xffffffff);
		
		description = new Paint();
		description.setColor(0x88dddddd);
		description.setAntiAlias(true);
	}
	
	public void setPlayDescription(String des) {
		playDescription = des;
	}
	
	public void onDraw(Canvas c) {
		Log.d("PitchMap", ""+getHeight());
		c.drawLine(120, 102, 120, 223, strikeZone);
		c.drawLine(200, 102, 200, 223, strikeZone);
		c.drawLine(120, 102, 200, 102, strikeZone);
		c.drawLine(120, 223, 200, 223, strikeZone);
		c.drawLine(0, 286, 320, 286, strikeZone);
		for(Gameday.Pitch p : mActivity.pitches) {
			Paint paint = new Paint();
			paint.setAntiAlias(true);
			paint.setColor(p.getColour());
			c.drawCircle(p.x, p.y, 10, paint);
			c.drawText(p.num+"", p.x, p.y+4, text);
		}
		
		if(playDescription != null) {
			int lines = ((int)text.measureText(playDescription)/300)+1;
			Log.d("Lines", ""+lines);
			int height = (int) (lines * (text.descent()-text.ascent()));
			c.drawRoundRect(new RectF(2,2,318,8+height), 2, 2, description);
			int y = 10;
			String des = playDescription;
			while(true) {
				Path p = new Path();
				int cs = text.breakText(des, true, 300, null);
				
				p.moveTo(5, y);
				p.lineTo(315, y);
				if(cs == des.length()) {
					c.drawTextOnPath(des.toCharArray(), 0, cs, p, 0, 5, text);
					break;
				}
				while(des.charAt(cs) != ' ') cs--;
				c.drawTextOnPath(des.toCharArray(), 0, cs, p, 0, 5, text);
				des = des.substring(cs+1);
				y += 1+text.descent()-text.ascent();
			}
		}
		
	}

	public String getPlayDescription() {
		// TODO Auto-generated method stub
		return playDescription;
	}

}

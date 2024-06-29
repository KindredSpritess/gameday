package com.kindred.gameday;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.DateFormat;
import android.text.format.Time;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.View.OnTouchListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;


/** 
 * Access the internet to download the list of games happening
 * today.
 * 
 * @author Douglas Catchpole
 *
 */
public class Scoreboard extends Activity {
	
	private static final String TAG = "Gameday";
	private List<Game> games = new ArrayList<Game>();
	private GridView gridview;
	private String year, month, date;
	private boolean refresh = true; 
	
	private static enum GameStatus {
		IN_PROGRESS,
		FINAL,
		PREVIEW,
		POSTPONED
	};
	private static enum Bases {
		EMPTY,
		FIRST,
		SECOND,
		THIRD,
		FIRSTSECOND,
		FIRSTTHIRD,
		SECONDTHIRD,
		LOADED
	};
	public Handler mHandler;
	private GregorianCalendar gc;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.scoreboard);
        setProgressBarIndeterminate(true);
        initDate();
        gridview = (GridView) findViewById(R.id.gridview);
        gridview.setAdapter(new GameAdapter(this));
        gridview.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				Log.d(TAG, "clicked");
				final Intent intent = new Intent(Scoreboard.this, Gameday.class);
				Game g = (Game) parent.getAdapter().getItem(position);
				intent.putExtra("year", year);
				intent.putExtra("month", month);
				intent.putExtra("date", date);
				intent.putExtra("gameid", g.link);
				intent.putExtra("homeName", g.home);
				intent.putExtra("awayName", g.away);
		        Scoreboard.this.startActivity(intent);				
			}
        });
        gridview.setOnTouchListener(new OnTouchListener() {
        	private static final int TIMEOUT = 500;
        	private static final int DEVIATE = -10;
        	private static final int SLANT = 100;
        	private static final int MOVE = 50;
        	private boolean straightLeft;
        	private boolean straightRight;
        	private boolean moved;
        	private long start;
        	private Vector<Integer> xs = new Vector<Integer>();
        	private int startY;
        	
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				Log.d(TAG, "Action: " + event.getAction());
				if(event.getAction() == MotionEvent.ACTION_DOWN) {
					straightLeft = straightRight = true;
					moved = false;
					startY = (int)event.getY();
					xs.clear();
					start = event.getEventTime();
				}
				if(event.getAction() == MotionEvent.ACTION_MOVE) {
					if(!(straightLeft || straightRight)) return false;
					if(event.getEventTime() - start > TIMEOUT) {
						straightLeft = straightRight = false;
						return false;
					}
					int len = event.getHistorySize();
					for(int i = 0; i < len; i++) {
						xs.add((int)event.getHistoricalX(i));
					}
					xs.add((int)event.getX());
					for(int i : xs) {
						if(i - event.getX() < DEVIATE) straightLeft = false;
						if(event.getX() - i < DEVIATE) straightRight = false;
						if(!(straightLeft || straightRight)) Log.d(TAG, "DEVIATE");
					}
					if(!moved) {
						int move = (int) (event.getX() < xs.firstElement() ? 
								xs.firstElement() - event.getX() :
								event.getX() - xs.firstElement());
						if(move > MOVE) moved = true;
					}
					int slant = (int) (event.getY() - startY);
					if(SLANT < slant || SLANT < slant * -1) {
						straightLeft = straightRight = false;
						Log.d(TAG, "Slant");
					}
					return true;
				}
				if(event.getAction() == MotionEvent.ACTION_UP) {
					if(moved && straightLeft) {
						setProgressBarIndeterminateVisibility(true);
						gc.add(GregorianCalendar.DATE, 1);
						resetDate();
						mHandler.post(new GetScoreOnceThread());
						return true;
					}
					if(moved && straightRight) { 
						setProgressBarIndeterminateVisibility(true);
						gc.add(GregorianCalendar.DATE, -1);
						resetDate();
						mHandler.post(new GetScoreOnceThread());
						return true;
					}
					if(!moved) Log.d(TAG, "Not moved");
				}
				return false;
			}
        	
        });
        mHandler = new Handler();
        mHandler.post(new GetScoreThread());
    }
       
    private void initDate() {
    	gc = new GregorianCalendar(TimeZone.getTimeZone("Pacific/Honolulu"));
    	year = gc.get(GregorianCalendar.YEAR) + "";
    	month = gc.get(GregorianCalendar.MONTH)+1 < 10 ? 
    			"0"+(gc.get(GregorianCalendar.MONTH)+1) : 
    			"" +(gc.get(GregorianCalendar.MONTH)+1);
    	date = gc.get(GregorianCalendar.DATE) < 10 ? 
    			"0"+(gc.get(GregorianCalendar.DATE)) : 
        			"" +(gc.get(GregorianCalendar.DATE));
    	setTitle("Gameday: " + date + "/" + month + "/" + year);
    }
    
    private void resetDate() {
    	year = gc.get(GregorianCalendar.YEAR) + "";
    	month = gc.get(GregorianCalendar.MONTH)+1 < 10 ? 
    			"0"+(gc.get(GregorianCalendar.MONTH)+1) : 
    			"" +(gc.get(GregorianCalendar.MONTH)+1);
    	date = gc.get(GregorianCalendar.DATE) < 10 ? 
    			"0"+(gc.get(GregorianCalendar.DATE)) : 
        			"" +(gc.get(GregorianCalendar.DATE));
    	setTitle("Gameday: " + date + "/" + month + "/" + year);
    }
    
    private void parseGameXml(String gameXml) {
    	try {
	    	SAXParserFactory spf = SAXParserFactory.newInstance();
			SAXParser sp = spf.newSAXParser();
			XMLReader xr = sp.getXMLReader();
			xr.setContentHandler(new GameLoader());
			xr.parse(new InputSource(new StringReader(gameXml)));
    	} catch (IOException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} 
	}

	private String getGameXml() {
    	StringBuffer url = new StringBuffer("http://gdx.mlb.com/components/game/mlb/");
        url.append("year_");
        url.append(year);
        url.append("/month_");
        url.append(month);
        url.append("/day_");
        url.append(date);
        url.append("/miniscoreboard.xml");
        try {
			HttpURLConnection uc = (HttpURLConnection) new URL(url.toString()).openConnection();
			uc.setRequestMethod("GET");
			uc.connect();
			BufferedReader in = new BufferedReader(new InputStreamReader(uc
					.getInputStream()), 8*1024);
			StringBuffer response = new StringBuffer();
			while(in.ready()) {
				response.append(in.readLine());
			}
			in.close();
			uc.disconnect();
			return response.toString();
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		return null;
    }
	
	private class Game {
		public GameStatus status;
		public boolean delay = false;
		public String link;
		public String home;
		public String away;
		public long start;
		public int inning;
		public boolean top;
		public int outs;
		public int homeScore;
		public int awayScore;
		public Bases bases;
	}
	
	private class GameLoader extends DefaultHandler {
		private Game g;
		
		public void startElement(String uri, String name, String qName, Attributes atts) {
			if(name.equals("game")) {
				// A game
				g = new Game();
				games.add(g);
				g.link = "gid_"+atts.getValue("id").replaceAll("[-\\/]", "_");
				String status = atts.getValue("status");
				refresh = false;
				if(status.equals("In Progress") || status.equals("Warmup")) {
					g.status = GameStatus.IN_PROGRESS;
					refresh = true;
				} else if(status.equals("Delayed") || status.equals("Delayed Start")) {
					g.status = GameStatus.IN_PROGRESS;
					g.delay = true;
					refresh = true;
				} else if(status.equals("Final") || status.equals("Game Over") || status.equals("Completed Early")) {
					g.status = GameStatus.FINAL;
				} else if(status.equals("Preview") || 
						status.equals("Pre-Game")) {
					g.status = GameStatus.PREVIEW;
				} else if(status.equals("Postponed")) {
					g.status = GameStatus.POSTPONED;
				}
				Log.d(TAG, status);
				switch(g.status) {					
				case IN_PROGRESS:
					g.outs = Integer.parseInt(atts.getValue("outs"));
					g.bases = Bases.values()[Integer.parseInt(atts.getValue("runner_on_base_status"))];
					g.top = atts.getValue("top_inning").equals("Y");
				case FINAL:
					g.homeScore = Integer.parseInt(atts.getValue("home_team_runs"));
					g.awayScore = Integer.parseInt(atts.getValue("away_team_runs"));
					g.inning = Integer.parseInt(atts.getValue("inning"));
				case PREVIEW:
				case POSTPONED:
					g.away = atts.getValue("away_name_abbrev");
					g.home = atts.getValue("home_name_abbrev");
				}
			}
			else if(name.equals("media")) {
				String ts = atts.getValue("start");
				int tzMin = ts.lastIndexOf('-');
				int tzPlu = ts.lastIndexOf('+');
				int tz = tzMin > tzPlu ? tzMin : tzPlu;
				if(tz > 0) ts = ts.substring(0, tz) + ".000" + ts.substring(tz,tz+3) + ":" + ts.substring(tz+3);
				Time t = new Time();
				t.parse3339(ts);
				g.start = t.toMillis(false);
			}
		}
		
		public void endElement(String uri, String name, String qName) throws SAXException {
			if(name.equals("game")) {
				g = null;
			}
		}
	}
	
	private class GameAdapter extends BaseAdapter {
	    private Context mContext;

	    public GameAdapter(Context c) {
	        mContext = c;
	    }

	    public int getCount() {
	        return games.size();
	    }

	    public Object getItem(int position) {
	        return games.get(position);
	    }

	    public long getItemId(int position) {
	        return 0;
	    }

	    // create a new ImageView for each item referenced by the Adapter
	    public View getView(int position, View convertView, ViewGroup parent) {
	    	ViewGroup v = (ViewGroup)((Activity)mContext).getLayoutInflater().inflate(R.layout.scoreboardgame, null);
	    	Game g = games.get(position);
	    	View colour = v.findViewById(R.id.game_colour);
	    	switch(g.status) {
	    	case FINAL:
	    		colour.setBackgroundColor(0xffff0000);
	    		break;
	    	case IN_PROGRESS:
	    		colour.setBackgroundColor(0xffffff00);
	    		break;
	    	case PREVIEW:
	    		colour.setBackgroundColor(0xff0000ff);
	    		break;
	    	case POSTPONED:
	    		colour.setBackgroundColor(0xffdddddd);
	    	}
	    	TextView tv;
	    	ViewGroup vg = (ViewGroup)v.findViewById(R.id.meta_content);
	    	tv = ((TextView)v.findViewById(R.id.away_name));
	    	tv.setText(g.away);
	    	tv = ((TextView)v.findViewById(R.id.home_name));
	    	tv.setText(g.home);
	    	switch(g.status) {
	    	case PREVIEW:
	    		// Start time
	    		Time t = new Time(Time.getCurrentTimezone());
	    		t.set(g.start);
	    		tv = new TextView(mContext);
	    		tv.setText(DateFormat.format("k:mm", t.toMillis(false)));
	    		tv.setGravity(Gravity.CENTER);
	    		tv.setWidth(48);
	    		tv.setTextSize(16);
	    		vg.addView(tv);
	    		break;
	    	case IN_PROGRESS:
	    		tv = ((TextView)v.findViewById(R.id.away_score));
	    		tv.setText(""+g.awayScore);
	    		tv = ((TextView)v.findViewById(R.id.home_score));
	    		tv.setText(""+g.homeScore);
	    		if(g.delay) {
	    			TextView d = new TextView(mContext);
	    			d.setText("Delay");
	    			d.setTextSize(16);
	    			d.setWidth(48);
	    			d.setGravity(Gravity.CENTER);
	    			vg.addView(d);
	    			break;
	    		}
	    		LinearLayout innings = new LinearLayout(mContext);
	    		innings.setMinimumWidth(48);
	    		innings.setOrientation(LinearLayout.HORIZONTAL);
	    		innings.setGravity(Gravity.CENTER_VERTICAL);
	    		ImageView iv = new ImageView(mContext);
	    		if(g.top) {
	    			iv.setImageResource(R.drawable.top);
	    		} else {
	    			iv.setImageResource(R.drawable.bot);
	    		}
	    		innings.addView(iv);
	    		TextView inn = new TextView(mContext);
	    		inn.setWidth(20);
	    		inn.setGravity(Gravity.CENTER);
	    		inn.setText(g.inning+"");
	    		innings.addView(inn);
	    		if(g.outs > 0) {
	    			iv = new ImageView(mContext);
	    			iv.setImageResource(R.drawable.out);
	    			innings.addView(iv);
	    		}
	    		if(g.outs > 1) {
	    			iv = new ImageView(mContext);
	    			iv.setImageResource(R.drawable.out);
	    			innings.addView(iv);
	    		}
	    		if(g.outs > 2) {
	    			iv = new ImageView(mContext);
	    			iv.setImageResource(R.drawable.out);
	    			innings.addView(iv);
	    		}
	    		vg.addView(innings);
	    		ImageView bases = new ImageView(mContext);
	    		bases.setImageResource((R.drawable.bases0 + g.bases.ordinal()));
	    		vg.addView(bases);
	    		break;
	    	case FINAL:
	    		tv = ((TextView)v.findViewById(R.id.away_score));
	    		tv.setText(""+g.awayScore);
	    		tv = ((TextView)v.findViewById(R.id.home_score));
	    		tv.setText(""+g.homeScore);
	    		if(g.awayScore > g.homeScore) {
	    			tv = ((TextView)v.findViewById(R.id.away_score));
		    		tv.setTextColor(0xffffffff);
		    		tv = ((TextView)v.findViewById(R.id.away_name));
		    		tv.setTextColor(0xffffffff);
	    		} else {
	    			tv = ((TextView)v.findViewById(R.id.home_score));
		    		tv.setTextColor(0xffffffff);
		    		tv = ((TextView)v.findViewById(R.id.home_name));
		    		tv.setTextColor(0xffffffff);
	    		}
	    		// Innings
	    		tv = new TextView(mContext);
	    		tv.setText("F/" + g.inning);
	    		tv.setGravity(Gravity.CENTER);
	    		tv.setWidth(48);
	    		tv.setTextSize(16);
	    		vg.addView(tv);
	    		break;
	    	case POSTPONED:
	    		tv = new TextView(mContext);
	    		tv.setText("PPD");
	    		tv.setGravity(Gravity.CENTER);
	    		tv.setWidth(48);
	    		tv.setTextSize(16);
	    		vg.addView(tv);
	    	}

	    	return v;
	    }
	}

	private class GetScoreThread extends Thread {
		
		public void run() {
			setProgressBarIndeterminateVisibility(true);
			games.clear();
			if(refresh) {
				parseGameXml(getGameXml());
				((BaseAdapter)gridview.getAdapter()).notifyDataSetChanged();
				mHandler.postDelayed(new GetScoreThread(), 1000*15);
			}
			setProgressBarIndeterminateVisibility(false);
			this.stop();
		}

	}
	
	private class GetScoreOnceThread extends Thread {
		
		public void run() {
			games.clear();
			((BaseAdapter)gridview.getAdapter()).notifyDataSetChanged();
			parseGameXml(getGameXml());
			((BaseAdapter)gridview.getAdapter()).notifyDataSetChanged();
			setProgressBarIndeterminateVisibility(false);
			this.stop();
		}

	}

	
}
/**
 * 
 */
package com.kindred.gameday;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SlidingDrawer;
import android.widget.TextView;
import android.widget.SlidingDrawer.OnDrawerCloseListener;
import android.widget.SlidingDrawer.OnDrawerOpenListener;

/**
 * @author Douglas Catchpole
 *
 */
public class Gameday extends Activity {
	public static enum PitchType {BALL, STRIKE, INPLAY};
	private static final String TAG = "Gameday";
	private String gameURI;
	private String homeName;
	private String awayName;
	private int batterNum = 0;
	private HashMap<Integer, Player> away = new HashMap<Integer, Player>();
	private HashMap<Integer, Player> home = new HashMap<Integer, Player>();
	private ArrayList<String[]> innings = new ArrayList<String[]>();
	public ArrayList<Pitch> pitches = new ArrayList<Pitch>();
	private int[][] summaries = new int[2][3];
	private int currentInning;
	private Handler mHandler;
	private PitchMap pitchMap;
	private SlidingDrawer inningsBar;
	

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gameday);
        loadIntentData();
        initialiseViews();
        loadBaseData();
        mHandler = new Handler();
        mHandler.post(new UpdateThread());
    }
    
    private void loadIntentData() {
    	Intent intent = getIntent();
    	StringBuffer sb = new StringBuffer("http://gdx.mlb.com/components/game/mlb/year_");
    	sb.append(intent.getStringExtra("year"));
    	sb.append("/month_");
    	sb.append(intent.getStringExtra("month"));
    	sb.append("/day_");
    	sb.append(intent.getStringExtra("date"));
    	sb.append("/");
    	sb.append(intent.getStringExtra("gameid"));
    	sb.append("/");
    	gameURI = sb.toString();
    	homeName = intent.getStringExtra("homeName");
    	awayName = intent.getStringExtra("awayName");
    }
    
    private String getGamedayXML(String file) {
    	String url = gameURI + file;
        try {
			HttpURLConnection uc = (HttpURLConnection) new URL(url).openConnection();
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
    
    private void getPlayers() {
    	String players = getGamedayXML("players.xml");
    	try {
	    	SAXParserFactory spf = SAXParserFactory.newInstance();
			SAXParser sp = spf.newSAXParser();
			XMLReader xr = sp.getXMLReader();
			xr.setContentHandler(new PlayerLoader());
			xr.parse(new InputSource(new StringReader(players)));
			Log.d(TAG, "Home: " + home.size() +  ", Away: " + away.size());
    	} catch (IOException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} 
    }
    
    private void loadBaseData() {
    	getPlayers();
    	// Fill views
		TextView tv;
		tv = (TextView)findViewById(R.id.away_name);
		tv.setText(awayName);
		tv = (TextView)findViewById(R.id.home_name);
		tv.setText(homeName);
		update();
    }
    
    private void update() {
    	String boxscore = getGamedayXML("plays.xml");
    	// Parse the linescore
    	try {
	    	SAXParserFactory spf = SAXParserFactory.newInstance();
			SAXParser sp = spf.newSAXParser();
			XMLReader xr = sp.getXMLReader();
			xr.setContentHandler(new PlayLoader());
			int oldBatter = batterNum;
			xr.parse(new InputSource(new StringReader(boxscore)));
			if(batterNum != oldBatter) {
				boxscore = getGamedayXML("miniscoreboard.xml");
				xr.setContentHandler(new LineScoreLoader());
				xr.parse(new InputSource(new StringReader(boxscore)));
				updateLineScore();
				getInningDetails(currentInning/2);
				// Handle
		    	ViewGroup vg = (ViewGroup)inningsBar.findViewById(R.id.handle);
		    	Log.d(TAG, vg.getChildCount()+"");
		    	for(int i = 0; i < currentInning/2; i++) {
		    		vg.getChildAt(i).setEnabled(true);
		    		((Button)vg.getChildAt(i)).setClickable(true);
		    	}
			}
    	} catch (IOException e) {
			e.printStackTrace();
			return;
		} catch (SAXException e) {
			e.printStackTrace();
			return;
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
			return;
		}
		pitchMap.invalidate();
    }
    
    private void updateLineScore() {		
		LinearLayout la, lh;
		la = (LinearLayout)findViewById(R.id.away_innings);
		lh = (LinearLayout)findViewById(R.id.home_innings);
		int i = 0;
		for(String[] inn : innings) {
			if(la.getChildAt(i) == null) {
				addInning(la, lh, i+1);
			}
			((TextView)la.getChildAt(i)).setText(inn[0]);
			((TextView)la.getChildAt(i)).setBackgroundResource(R.drawable.linebox);
			((TextView)lh.getChildAt(i)).setText(inn[1]);
			((TextView)lh.getChildAt(i)).setBackgroundResource(R.drawable.linebox);
			i++;
			if(currentInning/2 == i) {
				TextView tv = currentInning%2==1 ? ((TextView)lh.getChildAt(i-1)) : ((TextView)la.getChildAt(i-1));
				tv.setBackgroundResource(R.drawable.lineboxcurrent);
			}
		}
		((TextView)findViewById(R.id.gameday_innings)).setText(""+(currentInning/2));
		((ImageView)findViewById(R.id.gameday_halfinning)).setImageResource(
				currentInning%2 == 0 ?
						R.drawable.top :
						R.drawable.bot
		);
		la = (LinearLayout)findViewById(R.id.away_summaries);
		lh = (LinearLayout)findViewById(R.id.home_summaries);
		i = 0;
		while(i < 3) {
			((TextView)la.getChildAt(i)).setText(""+summaries[0][i]);
			((TextView)lh.getChildAt(i)).setText(""+summaries[1][i]);
			i++;
		}
    }
    
    private void addInning(ViewGroup la, ViewGroup lh, int inning) {
    	TextView tv;
    	tv = (TextView)getLayoutInflater().inflate(R.layout.linescorebox, null);
		tv.setWidth(23);
		la.addView(tv);
		tv = (TextView)getLayoutInflater().inflate(R.layout.linescorebox, null);
		tv.setWidth(23);
		lh.addView(tv);
		tv = (TextView)getLayoutInflater().inflate(R.layout.linescorebox, null);
		tv.setText(""+inning);
		tv.setWidth(23);
		tv.setPaintFlags(Paint.ANTI_ALIAS_FLAG | Paint.FAKE_BOLD_TEXT_FLAG);
		((ViewGroup)findViewById(R.id.innings_headers)).addView(tv);
		
    	ViewGroup vg = (ViewGroup)inningsBar.findViewById(R.id.handle);
    	Button b = (Button)getLayoutInflater().inflate(R.layout.inning_button, null);
    	b.setText(""+inning);
    	b.setEnabled(true);
    	b.setClickable(true);
    	b.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Log.d(TAG, ((Button)v).getText().toString());
				getInningDetails(Integer.parseInt(((Button)v).getText().toString()));
				((TextView)inningsBar.findViewById(R.id.grabber)).setText("Inning " + ((Button)v).getText().toString());
			}
    	});
    	vg.addView(b);
    }
    
    private void initialiseViews() {
    	TextView tv; 
    	tv = (TextView)findViewById(R.id.home_name);
    	tv.setPaintFlags(Paint.ANTI_ALIAS_FLAG | Paint.FAKE_BOLD_TEXT_FLAG);
    	tv = (TextView)findViewById(R.id.away_name);
    	tv.setPaintFlags(Paint.ANTI_ALIAS_FLAG | Paint.FAKE_BOLD_TEXT_FLAG);
    	setTitle(awayName + " @ " + homeName);
    	ViewGroup ls = (ViewGroup)findViewById(R.id.innings_headers);
    	for(int i = 0; i < 9; i++) {
    		tv = (TextView)getLayoutInflater().inflate(R.layout.linescorebox, null);
    		tv.setText(""+(i+1));
    		tv.setPaintFlags(Paint.ANTI_ALIAS_FLAG | Paint.FAKE_BOLD_TEXT_FLAG);
    		tv.setWidth(23);
    		ls.addView(tv);
    	}
    	ls = (ViewGroup)findViewById(R.id.away_innings);
    	for(int i = 0; i < 9; i++) {
    		tv = (TextView)getLayoutInflater().inflate(R.layout.linescorebox, null);
    		tv.setWidth(23);
    		ls.addView(tv);
    	}
    	ls = (ViewGroup)findViewById(R.id.home_innings);
    	for(int i = 0; i < 9; i++) {
    		tv = (TextView)getLayoutInflater().inflate(R.layout.linescorebox, null);
    		tv.setWidth(23);
    		ls.addView(tv);
    	}
    	ls = (ViewGroup)findViewById(R.id.summaries);
    	tv = (TextView)getLayoutInflater().inflate(R.layout.linescorebox, null);
    	tv.setPaintFlags(Paint.ANTI_ALIAS_FLAG | Paint.FAKE_BOLD_TEXT_FLAG);
    	tv.setWidth(23);
    	tv.setText("R");
    	ls.addView(tv);
    	tv = (TextView)getLayoutInflater().inflate(R.layout.linescorebox, null);
    	tv.setPaintFlags(Paint.ANTI_ALIAS_FLAG | Paint.FAKE_BOLD_TEXT_FLAG);
    	tv.setWidth(23);
    	tv.setText("H");
    	ls.addView(tv);
    	tv = (TextView)getLayoutInflater().inflate(R.layout.linescorebox, null);
    	tv.setPaintFlags(Paint.ANTI_ALIAS_FLAG | Paint.FAKE_BOLD_TEXT_FLAG);
    	tv.setWidth(23);
    	tv.setText("E");
    	ls.addView(tv);
    	ls = (ViewGroup)findViewById(R.id.away_summaries);
    	for(int i = 0; i < 3; i++) {
    		tv = (TextView)getLayoutInflater().inflate(R.layout.linescorebox, null);
    		tv.setPaintFlags(Paint.ANTI_ALIAS_FLAG | Paint.FAKE_BOLD_TEXT_FLAG);
    		tv.setWidth(23);
    		tv.setText("0");
    		ls.addView(tv);
    	}
    	ls = (ViewGroup)findViewById(R.id.home_summaries);
    	for(int i = 0; i < 3; i++) {
    		tv = (TextView)getLayoutInflater().inflate(R.layout.linescorebox, null);
    		tv.setPaintFlags(Paint.ANTI_ALIAS_FLAG | Paint.FAKE_BOLD_TEXT_FLAG);
    		tv.setWidth(23);
    		tv.setText("0");
    		ls.addView(tv);
    	}
    	pitchMap = new PitchMap(this);
    	((ViewGroup)findViewById(R.id.root)).addView(pitchMap);
    	inningsBar = (SlidingDrawer)getLayoutInflater().inflate(R.layout.inning, null);
    	inningsBar.setOnDrawerCloseListener(new OnDrawerCloseListener() {

			@Override
			public void onDrawerClosed() {
				((TextView)findViewById(R.id.grabber)).setText("Innings");
			}
    		
    	});
    	inningsBar.setOnDrawerOpenListener(new OnDrawerOpenListener() {

			@Override
			public void onDrawerOpened() {
				getInningDetails(currentInning/2);
				((TextView)findViewById(R.id.grabber)).setText("Inning " + currentInning/2);
			}
    		
    	});
    	((FrameLayout)findViewById(R.id.frame)).addView(inningsBar);
    	ViewGroup vg = (ViewGroup)inningsBar.findViewById(R.id.handle);
    	for(int i = 1; i < 10; i++) {
    		Button b = (Button)getLayoutInflater().inflate(R.layout.inning_button, null);
    		b.setText(""+i);
    		b.setOnClickListener(new OnClickListener() {
    			@Override
    			public void onClick(View v) {
    				Log.d(TAG, ((Button)v).getText().toString());
    				getInningDetails(Integer.parseInt(((Button)v).getText().toString()));
    				((TextView)inningsBar.findViewById(R.id.grabber)).setText("Inning " + ((Button)v).getText().toString());
    			}
        	});
    		vg.addView(b);
    	}
    }
    
    /**
     * Gets the selected innings plays and populates the innings
     * box with it.
     * 
     * @param num the innings detail to get
     */
    private boolean getInningDetails(int num) {
    	// Retrive the xml
    	String inning = getGamedayXML("inning/inning_"+num+".xml");
    	if(inning == null) {
    		// File not found
    		return false;
    	}
    	ArrayList<String> plays = new ArrayList<String>();
    	// Parse the inning
    	try {
	    	SAXParserFactory spf = SAXParserFactory.newInstance();
			SAXParser sp = spf.newSAXParser();
			XMLReader xr = sp.getXMLReader();
			xr.setContentHandler(new InningLoader(plays));
			xr.parse(new InputSource(new StringReader(inning)));
    	} catch (IOException e) {
			e.printStackTrace();
			return false;
		} catch (SAXException e) {
			e.printStackTrace();
			return false;
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
			return false;
		}
		
		// Populate the view
		ViewGroup inn = (ViewGroup)findViewById(R.id.plays);
		inn.removeAllViews();
		TextView tv;
		tv = (TextView)getLayoutInflater().inflate(R.layout.inning_heading, null);
		tv.setText("Top");
		inn.addView(tv);
		for(String play : plays) {
			if(play == null) {
				tv = (TextView)getLayoutInflater().inflate(R.layout.inning_heading, null);
				tv.setText("Bottom");
			} else {
				tv = (TextView)getLayoutInflater().inflate(R.layout.inning_play, null);
				tv.setText(play);
			}
			inn.addView(tv);
			
		}
		
		return true;
		
    }
    
    private class Player {
    	public int id;
    	public String first;
    	public String last;
    	public String num;
    	public String position;
    	public String avg;
    	public int hr;
    	public int rbi;
    	public int wins;
    	public int losses;
    	public String era;
    }
    
    class Pitch {
    	public int num;
    	public PitchType type;
    	public float x, y;
    	public String des;
    	
    	public Pitch(int num, float x, float y, String type, String des) {
    		this.num = num;
    		// Size 286
    		// Max Y = 205
    		// Min Y = 
    		// SBottom = 165
    		// STop
    		
    		this.x = ((x-200)/200)*-320;
    		this.y = (y/210)*261;
    		if(type.equals("B")) {
    			this.type = PitchType.BALL;
    		} else if(type.equals("S")) {
    			this.type = PitchType.STRIKE;
    		} else {
    			this.type = PitchType.INPLAY;
    		}
    		this.des = des;
    	}
    	
    	public int getColour() {
    		switch(type) {
    		case BALL:
    			return 0xff33dd33;
    		case STRIKE:
    			return 0xffdd3333;
    		case INPLAY:
    			return 0xff3333dd;
    		}
    		return 0xffffffff;
    	}
    	
    }
    
    private class PlayerLoader extends DefaultHandler {
    	private HashMap<Integer, Player> players;
    	
    	public void startElement(String uri, String name, String qName, Attributes atts) {
    		if(name.equals("team")) {
    			if(atts.getValue("type").equals("home")) {
    				players = home;
    			} else {
    				players = away;
    			}
    		} else if(name.equals("player")) {
    			if(!atts.getValue("status").equals("A")) return;
    			Player p = new Player();
    			p.id = Integer.parseInt(atts.getValue("id"));
    			p.first = atts.getValue("first");
    			p.last = atts.getValue("last");
    			p.num = atts.getValue("num");
    			p.position = atts.getValue("position");
    			p.avg = atts.getValue("avg");
    			p.hr = Integer.parseInt(atts.getValue("hr"));
    			p.rbi = Integer.parseInt(atts.getValue("rbi"));
    			if(p.position.equals("P")) {
    				p.wins = Integer.parseInt(atts.getValue("wins"));
    				p.losses = Integer.parseInt(atts.getValue("losses"));
    				p.era = atts.getValue("era");
    			}
    			Log.d(TAG, "player: " + p.id + " " + p.last);
    			players.put(p.id, p);
    		}
    	}
    }
    
    private class LineScoreLoader extends DefaultHandler {
    	
    	public LineScoreLoader() {
    		super();
    		innings.clear();
    	}
    	
    	public void startElement(String uri, String name, String qName, Attributes atts) {
    		if(name.equals("r")) {
    			int off = atts.getIndex("away");
    			for(int i = 0; i < 2; i++) {
    				summaries[i][0] = Integer.parseInt(atts.getValue(i+off));
    			}
    		} else if(name.equals("h")) {
    			int off = atts.getIndex("away");
    			for(int i = 0; i < 2; i++) {
    				summaries[i][1] = Integer.parseInt(atts.getValue(i+off));
    			}
    		} else if(name.equals("e")) {
    			int off = atts.getIndex("away");
    			for(int i = 0; i < 2; i++) {
    				summaries[i][2] = Integer.parseInt(atts.getValue(i+off));
    			}
    		} else if(name.equals("inning")) {
    			String[] inn = {atts.getValue("away"), atts.getValue("home")};
    			Log.d(TAG, inn.toString());
    			innings.add(inn);
    		}
    	}

    }

    private class PlayLoader extends DefaultHandler {
    	private int bases = 0;
    	private int pitchNum = 0;
    	private boolean top;
    	private boolean linescore;
    	
    	public PlayLoader() {
    		super();
    		pitches.clear();
    		((ImageView)findViewById(R.id.gamedaybases)).setImageResource(R.drawable.gamebases0);
    		pitchMap.setPlayDescription(null);
    	}
    	
    	public void startElement(String uri, String name, String qName, Attributes atts) {
    		if(name.equals("game")) {
    			if(atts.getValue("o") == null) return;
    			top = atts.getValue("top_inning").equals("T");
    			currentInning = Integer.parseInt(atts.getValue("inning"))*2 + (top ? 0 : 1);
    			
    			// Update outs
    			int i = 0;
    			int outs = Integer.parseInt(atts.getValue("o"));
    			while(i < outs) {
    				((ImageView)findViewById(R.id.out1+i)).setImageResource(R.drawable.gameout);
    				i++;
    			} while(i < 3) {
    				((ImageView)findViewById(R.id.out1+i)).setImageResource(R.drawable.gamebubble);
    				i++;
    			}
    			String balls = atts.getValue("b");
    			String strikes = atts.getValue("s");
    			((TextView)findViewById(R.id.gameday_ballsstrikes)).setText(balls + " - " + strikes);
    		} else if(name.equals("man")) {
    			if(atts.getValue("bnum") == null) return; 
    			int runner = Integer.parseInt(atts.getValue("bnum"));
    			if(runner == 4) return;
    			bases += 1 << (runner - 1);
    			((ImageView)findViewById(R.id.gamedaybases)).setImageResource(R.drawable.gamebases0+bases);
    		}
    		else if(name.equals("batter")) {
    			TextView tv = (TextView)findViewById(R.id.gameday_hitter);
    			StringBuffer s = new StringBuffer("#");
    			batterNum = Integer.parseInt(atts.getValue("pid"));
    			Player p;
    			Log.d(TAG, atts.getValue("pid") + top);
    			p = away.get(batterNum);
    			if(p == null) {
    				p = home.get(batterNum);
    			}
    			s.append(p.num);
    			s.append(" ");
    			s.append(p.first.charAt(0));
    			s.append(", ");
    			s.append(p.last);
    			s.append(" ");
    			//s.append(atts.getValue("position"));
    			
    			tv.setText(s.toString());
    		} else if(name.equals("pitcher")) {
    			TextView tv = (TextView)findViewById(R.id.gameday_pitcher);
    			StringBuffer s = new StringBuffer(" #");
    			Player p;
    			p = away.get(Integer.parseInt(atts.getValue("pid")));
    			if(p == null) {
    				p = home.get(Integer.parseInt(atts.getValue("pid")));
    			}
    			s.append(p.num);
    			s.append(" ");
    			s.append(p.first.charAt(0));
    			s.append(", ");
    			s.append(p.last);
    			s.append(" ");
    			s.append("P");
    			
    			tv.setText(s.toString());
    		} else if(name.equals("batter")) {
    			Player p;
    			p = away.get(Integer.parseInt(atts.getValue("pid")));
    			if(p == null) {
    				p = home.get(Integer.parseInt(atts.getValue("pid")));
    			}
    			p.position = atts.getValue("position");
    		} else if(name.equals("p")) {
    			if(atts.getValue("x") != null) {
    				// Add to pitch list
    				Pitch p = new Pitch(
    						++pitchNum,
    						Float.parseFloat(atts.getValue("x")),
    						Float.parseFloat(atts.getValue("y")),
    						atts.getValue("type"),
    						atts.getValue("des"));
    				pitches.add(p);
    			}
    		} else if(name.equals("atbat")) {
    			String des = atts.getValue("des");
    			if(des == null || des.length() == 0) pitchMap.setPlayDescription(null);
    			else pitchMap.setPlayDescription(des);
    		}
    	}
    	
    	public void endElement(String uri, String name, String qName) {
    		if(name.equals("atbat") && pitchMap.getPlayDescription() == null && pitches.size() != 0) {
    			pitchMap.setPlayDescription(pitches.get(pitches.size()-1).des);
    		}
    	}
    	
    }

    private class InningLoader extends DefaultHandler {
    	private ArrayList<String> plays;
    	
    	public InningLoader(ArrayList<String> plays) {
    		this.plays = plays;
    	}
    	
    	public void startElement(String uri, String name, String qName, Attributes atts) {
    		if(name.equals("bottom")) plays.add(null);
    		if(name.equals("atbat")) {
    			String outs = " outs.";
    			if(atts.getValue("o").equals("1")) outs = " out.";
    			plays.add(("T".equals(atts.getValue("score"))?"+":"")+
    					atts.getValue("des") + " " + atts.getValue("o") + outs);
    		}
    		if(name.equals("action")) {
    			plays.add("="+atts.getValue("des"));
    		}
    	}
    	
    }

    
    private class UpdateThread extends Thread {
		
		public void run() {
			setProgressBarIndeterminate(false);
			update();
			mHandler.postDelayed(new UpdateThread(), 1000*5);
			this.stop();
		}

	}
}

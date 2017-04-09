/* 
 * This source code file is part of the WebELS Software System.
 * WebELS is under the WebELS Software License Agreement. 
 * 
 *        ***Preamble of WebELS Software License Agreement***
 *
 * This Software License Agreement (hereinafter referred to as License) 
 * is a legal agreement between the User (either an individual or an entity, 
 * who will be referred to in this License as User) and the WebELS Project 
 * (represented by the project leader and patent owner, and will be referred 
 * to in this License as WebELS) of National Institute of Informatics for 
 * the use of WebELS Software (Software). By downloading, installing, copying, 
 * modifying, redistributing, or using the Software, the User is agreeing to 
 * be bound by the terms and conditions of this License. If you do not agree 
 * to the terms and conditions of this License, do not download, install, copy, 
 * modify, redistribute or use in any way as a whole or any part of the Software.
 *
 * For more details, see the WebELS Software License Agreement in 
 * license.txt in root directory of this package. If not found, 
 * see <http://webels.ex.nii.ac.jp/service/download/license/>
 *
 * Should you have any questions concerning this License, or if you desire 
 * to contact WebELS Project Leader for any reason, please write to:
 * 
 * Prof. Haruki Ueno, PhD
 * Professor Emeritus, National Institute of Informatics
 * 2-1-2 Hitotsubashi, Chiyodaku
 * 101-8430 Tokyo,Japan
 * Tel. +81-3-4212-2630
 * E-mail: ueno@nii.ac.jp
 * 
 * WebELS Project of the National Institute of Informatics (NII), Tokyo, Japan
 * http://webels.ex.nii.ac.jp/
 * Copyright © 2012 by WebELS Project of NII. All rights reserved.
 *
 */

package org.red5.webels.videomeeting;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Set;
import java.io.IOException;
import java.io.File;
import java.text.SimpleDateFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.red5.server.adapter.ApplicationAdapter;
import org.red5.server.api.IClient;
import org.red5.server.api.IConnection;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.Red5;
import org.red5.server.api.service.IPendingServiceCall;
import org.red5.server.api.service.IPendingServiceCallback;
import org.red5.server.api.service.IServiceCapableConnection;
import org.red5.server.api.service.ServiceUtils;
import org.red5.server.api.stream.IBroadcastStream;
import org.red5.server.api.stream.IPlayItem;
import org.red5.server.api.stream.IPlaylistSubscriberStream;
import org.red5.server.api.stream.IStreamAwareScopeHandler;
import org.red5.server.api.stream.ISubscriberStream;

import java.io.OutputStreamWriter;
import java.io.FilenameFilter;
import java.io.FileWriter;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.StringTokenizer;
import java.util.NoSuchElementException;
//import java.io.IOException;

import java.security.MessageDigest;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.sql.*;  //20120611

public class Application extends ApplicationAdapter implements IPendingServiceCallback, IStreamAwareScopeHandler {

	private Boolean DEBUG = true;
	//dbase address
	private static final String dbname = "meeting";
	private static final String dbuser = "webels";
	private static final String dbpass = "NTMzOWU1Mj";
	private static final String dbhost = "jdbc:mysql://127.0.0.1:3306/" + dbname + "?autoReconnect=true&characterSetResults=UTF-8&characterEncoding=UTF-8&useUnicode=yes";
	
	private Connection mySQLConn = null;
	private PreparedStatement stmt = null;
	private String query = null;

	ArrayList params_drawdata = new ArrayList();
	ArrayList params_pagestate = new ArrayList();
	ArrayList params_roomstate = new ArrayList();
//	ArrayList textpost_detail = new ArrayList(); // Adachi Version - Text Post by AJBERENA 20151118
	ArrayList textposts_detail = new ArrayList();
	ArrayList editor_detail = new ArrayList();
	ArrayList predict_drawdata = new ArrayList();
	ArrayList predict_pagestate = new ArrayList();
	double[] params_cursorpos = {0.0, 0.0};
	String[] slide_name = null;
	String[] slide_type = null;
	String[] data_file = null;
	String dataDir = "/var/WebELS/Meeting/tomcat/Meeting/data";
	String virtualDir = dataDir + "/virtual";
	String webappsDir = "/usr/local/red5/webapps";
	Boolean is_first_load_state = false;  // for checking the room state, 
	Boolean is_change_drawdata = false;  // for checking draw data
	String currentContentID = null; // currently used content other than the initial one
	int currentLectureMode = 1;  // current lecture mode -- 1 = cursor, 2 = annotation
	int currentPageNumber = 1;  // current page number for updating
	int currentPresenterID = 0; // presenter/lecturer ID
	int oldPageNumber = 1;  // old page number
	int predictPageNumber = 1;  // prediction page number
	int presenterStreamStatus = 0; // presenter/lecturer video stream status (on/off)
	int currentWhiteboardTab = 1; // Adachi Version - Whiteboard Tab by AJBERENA
	

	
	protected static Logger log = LoggerFactory.getLogger(Application.class);
	
	static {
		try {
			Class.forName("com.mysql.jdbc.Driver");
		}
		catch ( ClassNotFoundException exception ) {
			log.info("ClassNotFoundException " + exception.getMessage( ) );
		}
	}

	/** {@inheritDoc} */
    @Override
	public boolean appStart(IScope scope) {
		myDebug("Application Start");
		is_first_load_state = true;
		//connectDB();
		// init your handler here
		return true;
	}
	
	@Override
	public void appStop ( IScope scope ) {
		myDebug("Application Stop");
		closeDB();
	}

	/** {@inheritDoc} */
    @Override
	public boolean appConnect(IConnection conn, Object[] params) {
		myDebug("videomeeting.appConnect");
		// check the database connection
		//connectDB();
		
		IServiceCapableConnection service = (IServiceCapableConnection) conn;
		log.info("Client connected {} conn {}", new Object[]{conn.getClient().getId(), conn});
		service.invoke("setId", new Object[] { conn.getClient().getId() }, this);
		return true;
	}

	/** {@inheritDoc} */
    @Override
	public boolean appJoin(IClient client, IScope scope) {
		log.info("Client joined app {}", client.getId());
		// If you need the connection object you can access it.
		IConnection conn = Red5.getConnectionLocal();
		return true;
	}

	/** {@inheritDoc} */
    public void streamPublishStart(IBroadcastStream stream) {
		// Notify all the clients that the stream had been started
		if (log.isDebugEnabled()) {
			log.info("stream broadcast start: {}", stream.getPublishedName());
		}
		IConnection current = Red5.getConnectionLocal();
        for(Set<IConnection> connections : scope.getConnections()) {
            for (IConnection conn: connections) {
                if (conn.equals(current)) {
                    // Don't notify current client
                    continue;
                }

                if (conn instanceof IServiceCapableConnection) {
                    ((IServiceCapableConnection) conn).invoke("newStream",
                            new Object[] { stream.getPublishedName() }, this);
                    if (log.isDebugEnabled()) {
                        log.info("sending notification to {}", conn);
                    }
                }
            }
		}
	}

	/** {@inheritDoc} */
    public void streamRecordStart(IBroadcastStream stream) {
	}

	/** {@inheritDoc} */
    public void streamBroadcastClose(IBroadcastStream stream) {
	}

	/** {@inheritDoc} */
    public void streamBroadcastStart(IBroadcastStream stream) {
	}

	/** {@inheritDoc} */
    public void streamPlaylistItemPlay(IPlaylistSubscriberStream stream,
			IPlayItem item, boolean isLive) {
	}

	/** {@inheritDoc} */
    public void streamPlaylistItemStop(IPlaylistSubscriberStream stream,
			IPlayItem item) {

	}

	/** {@inheritDoc} */
    public void streamPlaylistVODItemPause(IPlaylistSubscriberStream stream,
			IPlayItem item, int position) {

	}

	/** {@inheritDoc} */
    public void streamPlaylistVODItemResume(IPlaylistSubscriberStream stream,
			IPlayItem item, int position) {

	}

	/** {@inheritDoc} */
    public void streamPlaylistVODItemSeek(IPlaylistSubscriberStream stream,
			IPlayItem item, int position) {

	}

	/** {@inheritDoc} */
    public void streamSubscriberClose(ISubscriberStream stream) {

	}

	/** {@inheritDoc} */
    public void streamSubscriberStart(ISubscriberStream stream) {
	}

	/**
	 * Get streams. called from client
	 * @return iterator of broadcast stream names
	 */
	public Set<String> getStreams() {
		IConnection conn = Red5.getConnectionLocal();
		return getBroadcastStreamNames(conn.getScope());
	}
	
	private void myDebug(String msg) {
		if (DEBUG) {
			log.info("DEBUG --> " + msg);
		}
	}
	
	/**
     *	　20120612 - added for changing presentation content on the fly
	 */ 
	private void connectDB () {
		myDebug("videomeeting.connectDB");
		try {
			//if (mySQLConn == null) {
				mySQLConn = DriverManager.getConnection(dbhost, dbuser, dbpass);
				myDebug("connect to database SUCCESS");
			//}
		}
		catch ( SQLException exception ) {
			myDebug("SQLException at connectDB: " + exception.getMessage( ) );
		}
	}
	
	private void closeDB () {
		myDebug("videomeeting.closeDB");
		try {
			if (mySQLConn != null) {
				mySQLConn.close();
				myDebug("database connection closed");
			}
		}
		catch ( SQLException exception ) {
			myDebug("SQLException at closeDB: " + exception.getMessage( ) );
		}
	}

	/**
	 * Handle callback from service call.
	 */
	public void resultReceived(IPendingServiceCall call) {
		log.info("Received result {} for {}", new Object[]{call.getResult(), call.getServiceMethodName()});
	}

	/**
	 * whiteboard initialization
	 */
	public void init_params(String roomid) throws IOException {
		Boolean is_content_change = false;
		
		myDebug("currentContentID, room id = " + currentContentID + ", " + roomid);
		// for initialing in the first time
		if (currentContentID == null) {
			currentContentID = roomid;
			clear_params();
		}
		// replace the roomid with a global data (was set by lecturer)
		if (!currentContentID.equals(roomid)) {
			is_content_change = true;
		}
		myDebug("init_params method; room id - " + currentContentID);
		myDebug("is_first_load_state="+is_first_load_state+"; is_content_change="+is_content_change); 
				
		//clear arrayList
		if (is_first_load_state || is_content_change) {
			//clear_params();
			
			getDataFile(false);	// load the data file
			loadRoomState(); 			// get the latest room state from file
			//currentLectureMode = 1; // set to cursor mode
		}
		
		is_first_load_state = false;
	}
	
	/**
	 * Set the current content id (global). used by lecturer
	 */
	public void changeContentID(String roomid, String streamroom) throws IOException {
		myDebug("changeContentID : roomid = " + roomid + ", streamroom = " + streamroom);
		// replace the roomid with a global data (was set by lecturer)
		if (currentContentID == null || !currentContentID.equals(roomid)) {
			currentContentID = roomid;
			myDebug("changeContentID : currentContentID = " + currentContentID);
			
			currentPageNumber = 1; // force to the first page
			oldPageNumber = 1; // force to the first page
			currentWhiteboardTab = 1; // force to first wb tab // Adachi Version
			
			clear_params();
			setLectureMode(1); // set to cursor mode
			getDataFile(false);	// load the data file
			// update current state
			genCurrentState();
		}
		
		try {
			String[] cmd = new String[3];
			cmd[0] = "/bin/sh";
			cmd[1] = "-c";
			cmd[2] = "/bin/rm -f " + webappsDir + "/" + streamroom + "/streams/" + currentContentID + " && ";
			cmd[2] += "/bin/ln -s " + virtualDir + "/" + currentContentID + "/flv " + webappsDir + "/" + streamroom + "/streams/" + currentContentID + "& ";
			myDebug(cmd[2]);
			Process p = Runtime.getRuntime().exec(cmd);
			p.waitFor();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Set the initial content id (global) upon Chaimman forced release
	 */
	public void setContentID(String initContentID) throws IOException {
		myDebug("setContentID : initContentID = " + initContentID);
		currentContentID = initContentID;
		currentPageNumber = 1;
		saveRoomState();
	}
	
	
	/**
	 * Get the current content id (global)
	 */
	public String getContentID() throws IOException {
		myDebug("getContentID : currentContentID = " + currentContentID);
		return currentContentID;
	}
	
	/**
	 * Set the current page number (global)
	 */
	public void setPageNumber(int page) throws IOException {
		currentPageNumber = page;
		myDebug("setPageNumber : currentPageNumber = " + currentPageNumber);
		saveRoomState();
		// update current state
		genCurrentState();
	}
	
	/**
	 * Get the current page number (global)
	 */
	public int getPageNumber() throws IOException {
		myDebug("getPageNumber : currentPageNumber = " + currentPageNumber);
		return currentPageNumber;
	}
	
	/**
	 * Set the current whiteboard tab (global)
	 */
	public void setWhiteboardTab(int page) throws IOException {
		currentWhiteboardTab = page;
		myDebug("setWhiteboardTab : currentWhiteboardTab = " + currentWhiteboardTab);
		saveRoomState();
		// update current state
		genCurrentState();
	}
	
	/**
	 * Get the current whiteboard tab (global)
	 */
	public int getWhiteboardTab() throws IOException {
		myDebug("getWhiteboardTab : currentWhiteboardTab = " + currentWhiteboardTab);
		return currentWhiteboardTab;
	}
	
		/**
	 * Set the current lecture mode used by lecturer
	 */
	public void setLectureMode(int mode) throws IOException {
		currentLectureMode = mode;
		myDebug("setLectureMode : currentLectureMode = " + currentLectureMode);
		saveRoomState();
		// update current state
		params_cursorpos[0] = -1;
		params_cursorpos[1] = -1;
		genCurrentState();
	}
	
	/**
	 * Get the current lecture mode
	 */
	public int getLectureMode() throws IOException {
		myDebug("getLectureMode : currentLectureMode = " + currentLectureMode);
		return currentLectureMode;
	}
	
	/**
	 * get Contents List
	 */
	public ArrayList<String> getContentsList(String contentID, String sort_order) {
		ArrayList<String> contentsList = new ArrayList<String>();  
		try {
			// make sure a database connection
			connectDB();
			
			query = "select * from course ";
			query += "where author=(select author from course where id=?) ";
			query += "and not isnull(categoryid) and not isnull(subcategoryid) ";
			query += "order by " + sort_order;			
			stmt = mySQLConn.prepareStatement(query);
			stmt.setString(1, contentID);
			myDebug("DEBUG INFO - GetContentsListQuery: " + stmt.toString());
			ResultSet rs = stmt.executeQuery();
			while (rs.next()) { 
				contentsList.add(rs.getString("id") + "::" + rs.getString("title"));			
			}
            stmt.close();
		}
		catch ( SQLException e ) {
			e.printStackTrace();
		}
		return contentsList;
	}
	
	/**
	 * clear parameters in array
	 */
	private void clear_params() {
		myDebug("clear_params");
		params_drawdata.clear();
		params_pagestate.clear();
		
		params_pagestate.add(0); // screen w
		params_pagestate.add(0); // screen h
		params_pagestate.add(1); // screen scale
		params_pagestate.add(0); // zoom
	}
	
	/**
	 * clear parameters in array (from erase method)
	 */
	public void clearDrawData() {
		myDebug("clearDrawData");
		
		clear_params();
		is_change_drawdata = true;
		// update current state
		genCurrentState();
	}
		
	public int getSize() {
		return getClients().size();
	}
	
	public long getTime() {
		return System.currentTimeMillis() / 1000L;
	}

	/*
	public Boolean announceLogin(IConnection conn) {
		myDebug("announceLogin");	
		IScope scope = Red5.getConnectionLocal().getScope();
		Set<IClient> clients = scope.getClients();
		Iterator iterator = clients.iterator();
		
		ArrayList client_list = new ArrayList();
		
		while (iterator.hasNext()) {
			IClient client = (IClient)iterator.next();
			client_list.add(new Object[]{client.getAttribute("handle_name"), client.getAttribute("camera_publish")});
		}
	
		iterator = clients.iterator();
		while ( iterator.hasNext() ) {
			IClient client = (IClient) iterator.next();
			Set<IConnection> connset = client.getConnections();
			Iterator<IConnection> itcon = connset.iterator();
		
			while (itcon.hasNext()) {
		 		conn = itcon.next();
		 		if (conn instanceof IServiceCapableConnection) {
		  			IServiceCapableConnection sc = (IServiceCapableConnection)conn;
					sc.invoke("announceAllMemberList", new Object[]{client_list});
				}
			}
		}
		return true;
	}

	public void publishCamera(IConnection conn) {
		conn.getClient().setAttribute("camera_publish", true);
		this.announceLogin(conn);
		
		return;
	}
	*/
		
	public void setDrawAttributes(int thickness, int color) {
		myDebug("setDrawAttributes");
		params_drawdata.add(thickness);
		params_drawdata.add(color);
	}
	
	public void setDrawData(double posX, double posY) {
		myDebug("setDrawData");
		params_drawdata.add(posX);
		params_drawdata.add(posY);
		
		// set changed flag
		is_change_drawdata = true;
		
		// end of draw
		// update current state
		if (posX == -1 && posY == -1) {
			genCurrentState();
		}
	}
	
	public void setCursorPosition(double posX, double posY) {
		myDebug("setCursorPosition");
		params_cursorpos[0] = posX;
		params_cursorpos[1] = posY;
		
		// update current state
		genCurrentState();
	}
	
	public void setPageState(int scb_x,int scb_y, double scale, int zoom_value) {
		myDebug("setPageState");
		params_pagestate.clear();
		params_pagestate.add(scb_x);
		params_pagestate.add(scb_y);
		params_pagestate.add(scale);
		params_pagestate.add(zoom_value);
		
		// set changed flag
		is_change_drawdata = true;
	}
	
	/**
	 * Text Post Function
	 **/
	
	// save text post data 
	public void setTextPostData(int pos_x, int pos_y, int box_w, int box_h, String textPost) {
		myDebug("setTextPostData");
		
	/*	textpost_detail.clear();
		textpost_detail.add(pos_x);
		textpost_detail.add(pos_y);
		textpost_detail.add(box_w);
		textpost_detail.add(box_h);
		textpost_detail.add(textPost);
	
		myDebug("setTextPostData : pos_x = " + textpost_detail.get(0));
		myDebug("setTextPostData : pos_y = " + textpost_detail.get(1));
		myDebug("setTextPostData : box_w = " + textpost_detail.get(2));
		myDebug("setTextPostData : box_h = " + textpost_detail.get(3));
		myDebug("setTextPostData : textPost = " + textpost_detail.get(4));
	
		textposts_detail.add(textpost_detail);
		myDebug("textposts_detail.size() : " + textposts_detail.size());
		
	*/
	
		ArrayList tmp = new ArrayList();
		tmp.add(pos_x);
		tmp.add(pos_y);
		tmp.add(box_w);
		tmp.add(box_h);
		tmp.add(textPost);
		
		textposts_detail.add(tmp);
		
		myDebug("setTextPostData : pos_x = " + tmp.get(0));
		myDebug("setTextPostData : pos_y = " + tmp.get(1));
		myDebug("setTextPostData : box_w = " + tmp.get(2));
		myDebug("setTextPostData : box_h = " + tmp.get(3));
		myDebug("setTextPostData : textPost = " + tmp.get(4));
	}
	
	// undo text post data 
	public void undoTextPostData() {
		myDebug("undoTextPostData");
		//textposts_detail.remove(textposts_detail.size() - 1);
		textposts_detail.remove(textposts_detail.size() - 1);
	}
	
	// remove all text post data 
	public void removeAllTextPostData() {
		myDebug("removeAllTextPostData");
		//textpost_detail.clear();
		textposts_detail.clear();
	}
	
	// save and generate text post data file
	public void saveTextPostData() {
		myDebug("saveTextPostData");
		
		if(textposts_detail.size() > 0) {
			
			String roomDir = new String(virtualDir + "/" + currentContentID);

			try {
				// generate text post data file
				File xmlTextPostDataFile = new File(roomDir + "/text/" + data_file[currentPageNumber -1] + ".xml");
				FileWriter xmlfw = new FileWriter(xmlTextPostDataFile);
				myDebug("saveTextPostData : file = " + xmlTextPostDataFile.toString());
			
				xmlfw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
				xmlfw.write("<textpost>\n");
			
				for (int i = 0; i < textposts_detail.size(); i++) {
					ArrayList tmp = new ArrayList((ArrayList)textposts_detail.get(i));
					xmlfw.write("\t<text pos_x=\"" + tmp.get(0) + "\" pos_y=\"" + tmp.get(1) + "\" box_w=\"" + tmp.get(2) + "\" box_h=\"" + tmp.get(3) + "\">" + tmp.get(4) + "</text>\n");
					myDebug("saveTextPostData : pos_x = " + tmp.get(0));
					myDebug("saveTextPostData : pos_y = " + tmp.get(1));
					myDebug("saveTextPostData : box_w = " + tmp.get(2));
					myDebug("saveTextPostData : box_h = " + tmp.get(3));
					myDebug("saveTextPostData : textflow = " + tmp.get(4));
				}
				xmlfw.write("</textpost>\n");
				xmlfw.close();	
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			myDebug("saveTextPostData : no text post");
		}
		// clear arrayList after saving to file
		textposts_detail.clear();
	}
	
	
	public ArrayList getTestPosts() {
		myDebug("getDrawData");
		return textposts_detail;
	}
	
	
	/**
	 * Save/retrieve data to array
	 */
	public Boolean saveLoadData(int old_page, int new_page, int scb_x,int scb_y, double scale, int zoom_value) throws IOException {
		myDebug("DEBUG INFO - saveLoadData - roomid : " + currentContentID + " , old page : " + old_page + " , new page : " + new_page);
		oldPageNumber = old_page; // keep an old page number for prediction
		setPageNumber(old_page);		
		keepData(scb_x, scb_y, scale, zoom_value);
		
		setPageNumber(new_page);
		saveRoomState();
		return loadData(new_page);
	}
	
	/**
	 * Save params to array
	 */
	public void keepData(int scb_x, int scb_y, double scale, int zoom_value) throws IOException {
		if (is_change_drawdata) {
			myDebug("keepData - roomid : " + currentContentID + " , page : " + currentPageNumber);
			
			// set page state data
			setPageState(scb_x, scb_y, scale, zoom_value);
			
			int i;
			String fn = new String(virtualDir + "/" + currentContentID + "/data/" + data_file[currentPageNumber -1]);
			myDebug("save data to file : " + fn);
			File fos1 = new File(fn);
			FileWriter fw = new FileWriter(fos1, false); //true for append and false for overwrite
			for (i = 0; i < params_pagestate.size(); i++) {
				fw.write(params_pagestate.get(i) + " ");
			}
			fw.write(" | ");
			for (i = 0; i < params_drawdata.size(); i++) {
				fw.write(params_drawdata.get(i) + " ");
			}
			fw.close();
			fos1 = null;
			
			// Copy annotation data to modified content 
			String roomDir = new String(virtualDir + "/" + currentContentID);
			try {
				String[] cmd = new String[3];
				cmd[0] = "/bin/sh";
				cmd[1] = "-c";
				cmd[2] = "cd " + roomDir + " && ";
				cmd[2] += "/bin/cp -a data backup ";
				myDebug(cmd[2]);
				Process p = Runtime.getRuntime().exec(cmd);
				p.waitFor();
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		// reset changed flag
		is_change_drawdata = false;
	}
	

	/**
	 * Load data
	 */
	public Boolean loadData(int page) throws IOException {
		// set page number
		setPageNumber(page);
		
		myDebug("loadData - roomid : " + currentContentID + " , page : " + currentPageNumber);
		clear_params();
		
		// check the prediction data
		// if yes, copy data from prediction
		// otherwise, directly load from file 
		if (predictPageNumber == currentPageNumber) {
			myDebug("use data from the prediction data");
			// copy data from prediction
			params_drawdata = new ArrayList(predict_drawdata);
			params_pagestate = new ArrayList(predict_pagestate);
		}
		else {
			String fn = new String(virtualDir + "/" + currentContentID + "/data/" + data_file[currentPageNumber -1]);
			myDebug("load data from file : " + fn);
			File fos = new File(fn);
			if (fos.exists()) {
				FileInputStream fis = new FileInputStream(fn);
				BufferedReader myInput = new BufferedReader(new InputStreamReader(fis));
				String thisLine = "";
				
				boolean is_state = true;
				String tokenStr = "";
				int pindex = 0;
				while ((thisLine = myInput.readLine()) != null) {
					StringTokenizer st = new StringTokenizer(thisLine);
					while (st.hasMoreTokens()) {
						tokenStr = st.nextToken();
						
						if (tokenStr.trim().compareTo("|") == 0) {
							is_state = false;
							continue;
						}
						
						// get page state
						if (is_state) {
							//params_pagestate.add(Double.parseDouble(tokenStr));
							params_pagestate.set(pindex, Double.parseDouble(tokenStr));
							pindex++;
						}
						else {
							params_drawdata.add(Double.parseDouble(tokenStr));
						}
					}
				}
				myInput.close();
				fis.close();
			}
			fos = null;
		}
		myDebug("end of load data");
		
		// predict a next content
		loadPredictData();
		
		// update current state
		params_cursorpos[0] = -1;
		params_cursorpos[1] = -1;
		genCurrentState();
		
		return true;
	}
	
	/**
	 * Load prediction data
	 */
	private void loadPredictData() throws IOException {
		
		// forward prediction
		if (oldPageNumber <= currentPageNumber) {
			if (currentPageNumber < (data_file.length - 1)) {
				predictPageNumber = currentPageNumber + 1;
			}
			else {
				return;
			}
		}
		// backward prediction
		else {
			if (currentPageNumber > 1) {
				predictPageNumber = currentPageNumber - 1;	
			}
			else {
				return;
			}
		}
		myDebug("loadPredictData - old = " + oldPageNumber + ", current = " + currentPageNumber + ", prediction = " + predictPageNumber);
		
		// clear data 
		predict_drawdata.clear();
		predict_pagestate.clear();
		
		predict_pagestate.add(0); // screen w
		predict_pagestate.add(0); // screen h
		predict_pagestate.add(1); // screen scale
		predict_pagestate.add(0); // zoom
		
		String fn = new String(virtualDir + "/" + currentContentID + "/data/" + data_file[predictPageNumber -1]);
		myDebug("prediction data file : " + fn);
		File fos = new File(fn);
		if (fos.exists()) {
			FileInputStream fis = new FileInputStream(fn);
			BufferedReader myInput = new BufferedReader(new InputStreamReader(fis));
			String thisLine = "";
			
			boolean is_state = true;
			String tokenStr = "";
			int pindex = 0;
			while ((thisLine = myInput.readLine()) != null) {
				StringTokenizer st = new StringTokenizer(thisLine);
				while (st.hasMoreTokens()) {
					tokenStr = st.nextToken();
					
					if (tokenStr.trim().compareTo("|") == 0) {
						is_state = false;
						continue;
					}
					
					// get page state
					if (is_state) {
						predict_pagestate.set(pindex, Double.parseDouble(tokenStr));
						pindex++;
					}
					else {
						predict_drawdata.add(Double.parseDouble(tokenStr));
					}
				}
			}
			myInput.close();
			fis.close();
		}
		fos = null;
		
		myDebug("end of prediction");
	}
	
	private void saveRoomState() throws IOException {
		myDebug("saveRoomState : room = " + currentContentID);
		myDebug("saveRoomState : wbtab = " + currentWhiteboardTab);
		myDebug("saveRoomState : page = " + currentPresenterID);
		myDebug("saveRoomState : presenterid = " + currentPresenterID);
		myDebug("saveRoomState : streamstatus = " + presenterStreamStatus);
		myDebug("saveRoomState : lecturemode = " + currentLectureMode);
		
		params_roomstate.set(0, currentPageNumber);
		params_roomstate.set(1, currentContentID);
		params_roomstate.set(2, currentPresenterID);
		params_roomstate.set(3, presenterStreamStatus);
		params_roomstate.set(4, currentLectureMode);
		params_roomstate.set(5, currentWhiteboardTab);
		File fos = new File(virtualDir + "/" + currentContentID + "/wb_state");
		FileWriter fw = new FileWriter(fos, false); //true for append and false for overwrite
		for (int i = 0; i < params_roomstate.size(); i++) {
			fw.write(params_roomstate.get(i) + " ");
		}
		fw.close();
		fos = null;
	}
	
	private void loadRoomState() throws IOException {
		myDebug("loadRoomState : room = " + currentContentID);
		// initial data
		params_roomstate.clear();
		params_roomstate.add(1); // page number
		params_roomstate.add(1); // content id
		params_roomstate.add(1); // presenter id
		params_roomstate.add(1); // presenter video stream status
		params_roomstate.add(1); // presentation mode
		params_roomstate.add(1); // whiteboard tab
		
		File fos = new File(virtualDir + "/" + currentContentID + "/wb_state");
		if (fos.exists()) {
			myDebug("loadRoomState : load existing data from file");
			FileInputStream fis = new FileInputStream(virtualDir + "/" + currentContentID + "/wb_state");
			BufferedReader myInput = new BufferedReader(new InputStreamReader(fis));
			String thisLine = "";
			String tokenStr = "";
			int pindex = 0;
			while ((thisLine = myInput.readLine()) != null) {
				StringTokenizer st = new StringTokenizer(thisLine);
				while (st.hasMoreTokens()) {
					tokenStr = st.nextToken();
					if (pindex == 0) {
						currentPageNumber = Integer.parseInt(tokenStr);
						params_roomstate.set(pindex, currentPageNumber);
						myDebug("loadRoomState: index - " + pindex + ", current slide - " + Integer.parseInt(tokenStr));
					}
					// content id (after changing content)
					else if (pindex == 1) {
						params_roomstate.set(pindex, Integer.parseInt(tokenStr));
						myDebug("loadRoomState: index - " + pindex + ", contentID - " + Integer.parseInt(tokenStr));
					}
					// presenter id (set to implement one-presenter policy)
					else if (pindex == 2) {
						params_roomstate.set(pindex, Integer.parseInt(tokenStr));
						myDebug("loadRoomState: index - " + pindex + ", Presenter clientID - " + Integer.parseInt(tokenStr));
					}
					// presenter video stream status 
					else if (pindex == 3) {
						params_roomstate.set(pindex, Integer.parseInt(tokenStr));
						myDebug("loadRoomState: index - " + pindex + ", Presenter video stream status - " + Integer.parseInt(tokenStr));
					}
					// lecture mode (1 - cursor or 2 - annotation)
					else if (pindex == 4) {
						currentLectureMode = Integer.parseInt(tokenStr);
						params_roomstate.set(pindex, currentLectureMode);
						myDebug("loadRoomState: index - " + pindex + ", Presentation mode - " + Integer.parseInt(tokenStr));
					}
					// whiteboard tab (1 - first whiteboard tab, and so on)
					else if (pindex == 5) {
						currentWhiteboardTab = Integer.parseInt(tokenStr);
						params_roomstate.set(pindex, currentWhiteboardTab);
						myDebug("loadRoomState: index - " + pindex + ", Whiteboard Tab - " + Integer.parseInt(tokenStr));
					}
					pindex++;
				}
			}
			myInput.close();
			fis.close();
		}
		else {
			myDebug("loadRoomState : initial for state");
		    // room state
		    currentPageNumber = 1;
		    currentLectureMode = 1;
			currentPresenterID = 0;
			presenterStreamStatus = 0;
			currentWhiteboardTab = 1;
			params_roomstate.set(0, currentPageNumber);// First Slide
			params_roomstate.set(1, currentContentID); // Currently used contentID or roomid
			params_roomstate.set(2, currentPresenterID); // 0 - No Presenter; clientID - Presenter's Client ID number (one-Presenter policy)
			params_roomstate.set(3, presenterStreamStatus); // 0 - No Video Stream; 1 - Video Stream
			params_roomstate.set(4, currentLectureMode); // Presentation mode; default is cursor mode
			params_roomstate.set(5, currentWhiteboardTab); // Whiteboard Tab (0 - first tab and so on)
					
			//create wb_state file
			FileWriter fw = new FileWriter(fos, false); //true for append and false for overwrite
			for (int i = 0; i < params_roomstate.size(); i++) {
				fw.write(params_roomstate.get(i) + " ");
			}
			fw.close();
		}
		
		// load draw data
		loadData(currentPageNumber);
		
		fos = null;
	}

	//-----------------------------------------------------------------------------------------------------
	
	public ArrayList getDrawData() {
		myDebug("getDrawData");
		return params_drawdata;
	}
	
	public double[] getCursorPosition() {
		myDebug("getCursorPosition");
		return params_cursorpos;
	}
	
	public ArrayList getPageState() {
		myDebug("getPageState");
		return params_pagestate;
	}
	
	public ArrayList getRoomState() {
		myDebug("getRoomState");
		return params_roomstate;
	}

	public Boolean isCourseAuthen(String roomNum, String author) {
		myDebug("isCourseAuthen : " + roomNum + "  " + author);
		Boolean isauth = false;
		try {
            // make sure a database connection
			connectDB();		
			query = "select count(id) as cid from course ";
			query += "where id=? and author=? ";
			stmt = mySQLConn.prepareStatement(query);
			stmt.setString(1, roomNum);
			stmt.setString(2, author);
			// execute query
			ResultSet rs = stmt.executeQuery();
			rs.next();
			if (rs.getInt("cid") > 0) {
				isauth = true;
			}
            stmt.close();
		}
		catch ( SQLException e ) {
			e.printStackTrace();
			myDebug("isCourseAuthen Error");
		}
		return isauth;
	}
		
	public boolean isConvertComplete(String roomid) {
		myDebug("isConvertComplete");
        boolean status = false;
		File gsLock = new File(virtualDir + "/" + roomid + "/gs.lock");
		status |= gsLock.exists();
		File OpenOfficeLock = new File(virtualDir + "/" + roomid + "/OpenOffice.lock");
		status |= OpenOfficeLock.exists();
		File ffmpegLock = new File(virtualDir + "/" + roomid + "/ffmpeg.lock");
		status |= ffmpegLock.exists();
		
		return !status;
	}
	
	public boolean genMetaXML(String roomid, Boolean edit, String author, String title, String category, String subcategory, String password, String viewpassword, String convOutput) {
		myDebug("genMetaXML");
		myDebug("genMetaXML ; " + convOutput);
		
		boolean status = false;
		String validkey = genValidKey();
		String roomDir = new String(virtualDir + "/" + roomid);
		String existingpwd = null;
		String existingpwd2 = null;
		
		try {
			if (edit) {
				existingpwd = getMetaDescription(roomid, "viewpasswordmd5", null);
				existingpwd = (existingpwd != null) ? existingpwd : "";
				existingpwd2 = getMetaDescription(roomid, "passwordmd5", null);
				existingpwd2 = (existingpwd2 != null) ? existingpwd2 : "";
			}
				
			File xmlDescFile = new File(roomDir + "/meta_description.xml");
			FileWriter fw = new FileWriter(xmlDescFile);
			fw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
			fw.write("<description>\n");
			fw.write("\t<title>" + title + "</title>\n");
			fw.write("\t<category subcategory=\"" + subcategory + "\">" + category +"</category>\n");
			if (password.trim().compareTo("") != 0) {
				if(edit) {
					// password was changed
					if (password.trim().compareTo(existingpwd2.trim()) != 0) {
						fw.write("\t<passwordmd5>" + md5Encode(password) + "</passwordmd5>\n");
					}
					// keeps an old password
					else {
						fw.write("\t<passwordmd5>" + existingpwd2.trim() + "</passwordmd5>\n");
					}
				} else {
					fw.write("\t<passwordmd5>" + md5Encode(password) + "</passwordmd5>\n");
				}
			}
			else {
				// not set a password
				fw.write("\t<passwordmd5></passwordmd5>\n");
			}
			if (viewpassword.trim().compareTo("") != 0) {
				if(edit) {
					// password was changed
					if (viewpassword.trim().compareTo(existingpwd.trim()) != 0) {
						fw.write("\t<viewpasswordmd5>" + md5Encode(viewpassword) + "</viewpasswordmd5>\n");
					}
					// keeps an old password
					else {
						fw.write("\t<viewpasswordmd5>" + existingpwd.trim() + "</viewpasswordmd5>\n");
					}
				} else {
					fw.write("\t<viewpasswordmd5>" + md5Encode(viewpassword) + "</viewpasswordmd5>\n");
				}
			}
			else {
				// not set a password
				fw.write("\t<viewpasswordmd5></viewpasswordmd5>\n");
			}
			fw.write("\t<validkey>" + validkey + "</validkey>\n"); 
			fw.write("\t<fileConversionOutput>" + convOutput + "</fileConversionOutput>\n");
			fw.write("</description>\n");
			fw.close();
			
			status = genCourseXML(roomid, edit, password, viewpassword, validkey);
			
			createDBCourse(roomid, author, edit);
		}
		catch (Exception e) {
			e.printStackTrace();
			status = false;
		}
		return status;
	}
	
	// this function is generate the courseXXXXXA.xml for compatible with java version
	private boolean genCourseXML(String roomid, Boolean edit, String password, String viewpassword, String validkey) {
		myDebug("genCourseXML");
		
		boolean status = false;
		String existingpwd = null;
		String existingpwd2 = null;
		
		if (edit) {
			existingpwd = getMetaDescription(roomid, "viewpasswordmd5", null); 
			existingpwd = (existingpwd != null) ? existingpwd : "";
			existingpwd2 = getMetaDescription(roomid, "passwordmd5", null);
			existingpwd2 = (existingpwd2 != null) ? existingpwd2 : "";
		}
		
		try {
			File xmlDescFile = new File(dataDir + "/course" + roomid + "A.xml");
			FileWriter fw = new FileWriter(xmlDescFile);
			fw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
			fw.write("<body>\n");
			fw.write("\t<p align=\"center\">\n");
			fw.write("\t<module name=\"Presentation Module\" ");
			fw.write("id=\"N10006\" ");
			// set view password
			if (viewpassword.trim().compareTo("") != 0) {
				if(edit) {
					// password was changed
					if (viewpassword.trim().compareTo(existingpwd.trim()) != 0) {
						fw.write("password=\"" + md5Encode(viewpassword) + "\" ");
					}
					// keeps an old password
					else {
						fw.write("password=\"" + existingpwd.trim() + "\" ");
					}
				} else {
					fw.write("password=\"" + md5Encode(viewpassword) + "\" ");
				}	
			}
			// set edit password
			if (password.trim().compareTo("") != 0) {
				if(edit) {
					// password was changed
					if (password.trim().compareTo(existingpwd2.trim()) != 0) {
						fw.write("password2=\"" + md5Encode(password) + "\" ");
					}
					// keeps an old password
					else {
						fw.write("password2=\"" + existingpwd2.trim() + "\" ");
					}
				} else {
					fw.write("password2=\"" + md5Encode(password) + "\" ");
				}
			}
			fw.write("validkey=\"" + validkey + "\" ");
			fw.write(">\n");
			fw.write("Y291cnNlNTE1NzY1MzNfbW9kdWxlXzkuZGF0\n");
			fw.write("\t</module>\n");
			fw.write("\t</p>\n");
			fw.write("</body>\n");
			fw.close();
			
			status = true;
		}
		catch (Exception e) {
			e.printStackTrace();
			status = false;
		}
		return status;
		
	}
	
	public boolean createDBCourse(String roomid, String author, Boolean edit) {
		myDebug("createDBCourse");
	
		boolean status = false;
		boolean isexisting = isExistingID(roomid);
		String title = getMetaDescription(roomid, "title", null);
		String category = getMetaDescription(roomid, "category", null);
		String subcategory = getMetaDescription(roomid, "category", "subcategory");
		
		try {
			// make sure a database connection
			connectDB();

			// already created from servlet
			if (isexisting) {
				// sql injection protection
				//query = " set names 'utf8'; set CHARACTER SET 'utf8'; ";
				query = "update course set title=?, CategoryID=?, SubCategoryID=?, lastmodified=sysdate() ";
				query += "where id=? ";
				stmt = mySQLConn.prepareStatement(query);
				stmt.setString(1, title);
				stmt.setString(2, category);
				stmt.setString(3, subcategory);
				stmt.setString(4, roomid);
				stmt.executeUpdate();
			}
			else {				
				// sql injection protection
				query = "insert into course (id, title, author, CategoryID, SubCategoryID, lastmodified) ";
				query += "values (?, ?, ?, ?, ?, sysdate()) ";				
				stmt = mySQLConn.prepareStatement(query);
				stmt.setString(1, roomid);
				stmt.setString(2, title);
				stmt.setString(3, author);
				stmt.setString(4, category);
				stmt.setString(5, subcategory);
				stmt.executeUpdate();
			}
			myDebug(query);
			myDebug("roomid : " + roomid + ", title : " + title + ", author : " + author + ", category : " + category + ", subcategory : " + subcategory);

			status = true;
		}
		catch (Exception e) {
			e.printStackTrace();
			status = false;
		}
		return status;
	}
	
	private boolean isExistingID(String roomid) {
		myDebug("isExistingID ");
		
		boolean status = false;
		try {
			// make sure a database connection
			connectDB();
			
			query = "select count(id) as rowcount from course ";
			query += "where id=?";
			stmt = mySQLConn.prepareStatement(query);
			stmt.setString(1, roomid);
			// execute query
			ResultSet rs = stmt.executeQuery();
			rs.next();
			if (rs.getInt("rowcount") > 0) {
				status = true;
			}
			rs.close();
		}
		catch ( SQLException e ) {
			e.printStackTrace();
		}
		return status;
	}
	
	// get data filename from XML file
	private void getDataFile(Boolean orig){
		myDebug("getDataFile");
		myDebug("Whiteboard Tab : " + currentWhiteboardTab);
		
		String roomDir;
		if (orig) {
			roomDir = new String(virtualDir + "/" + currentContentID + "/orig");
		}
		else {
			roomDir = new String(virtualDir + "/" + currentContentID);
		}
		try {
			
			
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document xml = db.parse(roomDir + "/content_description_" + Integer.toString(currentWhiteboardTab) + ".xml");
			NodeList nodeLst = xml.getElementsByTagName("slide");
			
			if (nodeLst.getLength() > 0) {
				slide_name = new String[nodeLst.getLength()];
				slide_type = new String[nodeLst.getLength()];
				data_file = new String[nodeLst.getLength()];
				for (int s = 0; s < nodeLst.getLength(); s++) {
				    Node node = nodeLst.item(s);
				    if (node.getNodeType() == Node.ELEMENT_NODE) {			  
				    	Element elmnt = (Element) node;
				    	NodeList nlc = elmnt.getChildNodes();
				    	
				    	slide_name[s] =  new String(((Node) nlc.item(0)).getNodeValue());
				    	slide_type[s] = new String(elmnt.getAttribute("type")); 
				    	data_file[s] = new String(elmnt.getAttribute("datafile"));   
				   }
				}
			}
		}catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	// get meta description from XML file
	private String getMetaDescription(String roomid, String element, String attribute){
		myDebug("getMetaDescription : room = " + roomid + ", element = " + element + ", attrib = " + attribute);
		
		String roomDir = new String(virtualDir + "/" + roomid);
		String value = null;
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document xml = db.parse(roomDir + "/meta_description.xml");
			NodeList nodeLst = xml.getElementsByTagName("description");

			for (int s = 0; s < nodeLst.getLength(); s++) {
			    Node node = nodeLst.item(s);			    
			    if (node.getNodeType() == Node.ELEMENT_NODE) {			  
			    	Element elmnt = (Element) node;
			      
			    	NodeList nlh = elmnt.getElementsByTagName(element);
			    	Element elm = (Element) nlh.item(0);
			    	NodeList nlc = elm.getChildNodes();
			      
			    	if (attribute == null) {
			    		value = ((Node) nlc.item(0) != null) ? ((Node) nlc.item(0)).getNodeValue() : null;
			    	}
			    	else {
			    		value = elm.getAttribute(attribute);
			    	}
			    }
			}			
		}catch(Exception e) {
			e.printStackTrace();
		}
		return value;
	}
	
	public boolean genEditorTemplate(String roomid, String contentNum, Boolean edit, Boolean orig, Boolean isnew) {
		myDebug("genEditorTemplate");
		
		// update the current content id
		currentContentID = roomid;
		// list image file
		getImgFiles(roomid, edit, orig);
		getFlvFiles(roomid, edit, orig);
		
		return saveEditorTemplate(roomid, contentNum, orig, isnew);
	}
	
	// function for list of files from content directory
	private void getImgFiles(String roomid, Boolean edit, Boolean orig) {
		myDebug("getImgFiles");
		String roomDir;
		if (orig) {
			roomDir = new String(virtualDir + "/" + roomid + "/orig");
		}
		else {
			roomDir = new String(virtualDir + "/" + roomid);
		}
		String imgDir = new String(roomDir + "/files/new");
		
		FilenameFilter filter = null;
		File tmpDir = new File(imgDir);
		if (tmpDir.exists()) {
			// filter for the .png or .jpg or .svg files 
			filter = new FilenameFilter() {
				public boolean accept(File dir, String name) {
					return (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".svg") || name.endsWith(".tiff") || name.endsWith(".tif") || name.endsWith(".bmp")) && (name.compareTo("blank.png") != 0);
				}
			};
			
			String[] imgFiles = tmpDir.list(filter);
			int p = 0;
			if (edit) {
				p = editor_detail.size();
			}
			Arrays.sort(imgFiles);
			for (int i=0; i<imgFiles.length; i++) {
				// create data for template 
				Boolean clear = (i==0) ? true : false;
				clear = (edit) ? false : clear;
				setEditorData(clear, (i + p + 1), "", imgFiles[i], "");
			}
		}
		
		// move from new directory to normal directory
		try {
			String[] cmd = new String[3];
			cmd[0] = "/bin/sh";
			cmd[1] = "-c";
			cmd[2] = "mv " + roomDir+ "/files/new/*.{png,jpg,svg,bmp,tiff,tif}" + " " + roomDir + "/files/ ";
			Process p = Runtime.getRuntime().exec(cmd);
			p.waitFor();
			cmd[2] = "mv " + roomDir+ "/files/new/thumb/*.{png,jpg,svg,bmp,tiff,tif}" + " " + roomDir + "/files/thumb/ ";
			p = Runtime.getRuntime().exec(cmd);
			p.waitFor();
			cmd[2] = "mv " + roomDir+ "/files/new/preview/*.{png,jpg,svg,bmp,tiff,tif}" + " " + roomDir + "/files/preview/ ";
			p = Runtime.getRuntime().exec(cmd);
			p.waitFor();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		tmpDir = null;
	}
	
private void getFlvFiles(String roomid, Boolean edit, Boolean orig) {
		myDebug("getFlvFiles");
		String roomDir;
		if (orig) {
			roomDir = new String(virtualDir + "/" + roomid + "/orig");
		}
		else {
			roomDir = new String(virtualDir + "/" + roomid);
		}
		String vidDir = new String(roomDir + "/flv/new");
		
		FilenameFilter filter = null;
		File tmpDir = new File(vidDir);
		if (tmpDir.exists()) {
			// filter for the .flv files 
			filter = new FilenameFilter() {
				public boolean accept(File dir, String name) {
					return (name.endsWith(".swf") || name.endsWith(".flv") );
				}
			};
			
			String[] flvFiles = tmpDir.list(filter);
			int p = 0;
			if (edit) {
				p = editor_detail.size();
			}
			Arrays.sort(flvFiles);
			for (int i=0; i<flvFiles.length; i++) {
				// create data for template 
				Boolean clear = (i==0) ? true : false;
				clear = (edit) ? false : clear;
				setEditorData(clear, (i + p + 1), "", flvFiles[i], "");
			}
		}
		
		// move from new directory to normal directory
		try {
			String[] cmd = new String[3];
			cmd[0] = "/bin/sh";
			cmd[1] = "-c";
			cmd[2] = "mv " + roomDir+ "/flv/new/*.{swf,flv}" + " " + roomDir + "/flv/ ";
			Process p = Runtime.getRuntime().exec(cmd);
			p.waitFor();
			cmd[2] = "mv " + roomDir+ "/flv/new/thumb/*.{swf,flv}" + " " + roomDir + "/flv/thumb/ ";
			p = Runtime.getRuntime().exec(cmd);
			p.waitFor();
			cmd[2] = "mv " + roomDir+ "/flv/new/preview/*.{swf,flv}" + " " + roomDir + "/flv/preview/ ";
			p = Runtime.getRuntime().exec(cmd);
			p.waitFor();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		tmpDir = null;
	}
	
	public boolean saveEditorTemplate(String roomid, String wbTabNum, Boolean orig, Boolean isnew) {
		myDebug("saveEditorTemplate");
		
		boolean status = false;
		String roomDir;
		if (orig) {
			roomDir = new String(virtualDir + "/" + roomid + "/orig");
		}
		else {
			roomDir = new String(virtualDir + "/" + roomid);
		}
				
		try {
			// for flex
			// in the future it should be get template for edting
			File xmlDescFile = new File(roomDir + "/content_description_" + wbTabNum + ".xml");
			FileWriter xmlfw = new FileWriter(xmlDescFile);
			xmlfw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
			xmlfw.write("<description>\n");
			xmlfw.write("\t<slides>\n");
			for (int i=0; i<editor_detail.size(); i++) {
				ArrayList tmp = new ArrayList((ArrayList)editor_detail.get(i));
				xmlfw.write("\t\t<slide page=\"" + tmp.get(0) + "\" title=\"" + tmp.get(1) + "\" info=\"" + tmp.get(3) + "\" datafile=\""  + tmp.get(4) + "\" type=\"" + tmp.get(5) + "\">" + tmp.get(2) + "</slide>\n");
			}
			xmlfw.write("\t</slides>\n");
			xmlfw.write("</description>\n");
			xmlfw.close();
			
			if (wbTabNum.equals("1")) {
				String[] cmd = new String[3];
				cmd[0] = "/bin/sh";
				cmd[1] = "-c";
				cmd[2] = "/bin/cp -f " + roomDir + "/content_description_" + wbTabNum + ".xml " + roomDir + "/content_description.xml ";
				Process p = Runtime.getRuntime().exec(cmd);
				myDebug(cmd[2]);
				p.waitFor();
			}
			
			
			if (orig) {
				setWorkingData(roomid);  // set working space data when create a new content or editing a content
			} else {
				backupWorkingData(roomid);  //set backup for modified content
			}
			
			// update data file
			getDataFile(orig);
			status = true;			
		}
		catch (Exception e) {
			e.printStackTrace();
			status = false;
		}
		return status;
	}
	
	public void setEditorData(boolean clean, int page, String title, String image, String info) {
		myDebug("setEditorData " + image);
		String type = null;
		// check first data
		if (clean) {
			myDebug ("First slide");
			editor_detail = new ArrayList();
		}
		ArrayList tmp = new ArrayList();
		String[] dataFile = image.split("\\.");
		
		if (dataFile[1].equals("flv") || dataFile[1].equals("swf")) {
			    type = "video";
		} else if (dataFile[1].equals("svg")){
				type = "vector";
		} else {
				type = "image";
		}
		
		tmp.add(page);
		tmp.add(title);
		tmp.add(image);
		tmp.add(info);
		tmp.add(dataFile[0]);
		tmp.add(type);
		editor_detail.add(tmp);
	}
	
	public void insertEditorData(String roomid, Boolean edit, Boolean orig) {
		myDebug("insertEditorData ");
		getImgFiles(roomid, edit, orig);
		getFlvFiles(roomid, edit, orig);
	}
	
	public void removeSlide(String roomid, String slideImg) {
		myDebug("removeSlide - " + slideImg);
		
		String roomDir = new String(virtualDir + "/" + roomid);
		try {
			String[] cmd = new String[3];
			cmd[0] = "/bin/sh";
			cmd[1] = "-c";
			cmd[2] = "/bin/rm -f " + roomDir + "/orig/files/" + slideImg + " && ";
			cmd[2] += "/bin/rm -f " + roomDir + "/orig/files/thumb/" + slideImg;
			Process p = Runtime.getRuntime().exec(cmd);
			p.waitFor();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void clearEditorData(String roomid) {
		myDebug("clearEditorData ");
		editor_detail = new ArrayList();
		
		String roomDir = new String(virtualDir + "/" + roomid);
		try {
			String[] cmd = new String[3];
			cmd[0] = "/bin/sh";
			cmd[1] = "-c";
			cmd[2] = "/bin/rm -rf " + roomDir + "/orig/files/* && ";
			cmd[2] += "/bin/rm -rf " + roomDir + "/orig/flv/* && ";
			cmd[2] += "/bin/rm -rf " + roomDir + "/orig/data/* ";
			Process p = Runtime.getRuntime().exec(cmd);
			p.waitFor();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void createBlankFile(String roomid, String newfn, Boolean orig) {
		myDebug("createBlankFile : " + newfn);
		
		String roomDir;
		if (orig) {
			roomDir = new String(virtualDir + "/" + roomid + "/orig");
		}
		else {
			roomDir = new String(virtualDir + "/" + roomid);
		}
		// move from new directory to normal directory
		try {
			String[] cmd = new String[3];
			cmd[0] = "/bin/sh";
			cmd[1] = "-c";
			cmd[2] = "cd " + roomDir + "/files/ && /bin/cp blank.png" + " " + newfn + " && ";
			cmd[2] += "cd " + roomDir + "/files/thumb && /bin/cp blank.png" + " " + newfn + " && ";
			cmd[2] += "cd " + roomDir + "/files/preview && /bin/cp blank.png" + " " + newfn + " ";
			Process p = Runtime.getRuntime().exec(cmd);
			p.waitFor();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	// Adachi Version
	public void createBlankWbTab(String roomid, String wbTabNum, String newfn, Boolean orig) {
		myDebug("createBlankWbTab : " + newfn);
		
		String roomDir;
		if (orig) {
			roomDir = new String(virtualDir + "/" + roomid + "/orig");
		}
		else {
			roomDir = new String(virtualDir + "/" + roomid);
		}
		// move from new directory to normal directory
		try {
			String[] cmd = new String[3];
			cmd[0] = "/bin/sh";
			cmd[1] = "-c";
			cmd[2] = "cd " + roomDir + "/files/ && /bin/cp blank.png" + " " + newfn + " && ";
			cmd[2] += "cd " + roomDir + "/files/thumb && /bin/cp blank.png" + " " + newfn + " && ";
			cmd[2] += "cd " + roomDir + "/files/preview && /bin/cp blank.png" + " " + newfn + " ";
			Process p = Runtime.getRuntime().exec(cmd);
			p.waitFor();		
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		
		try {
			// create content description file
			File xmlDescFile = new File(roomDir + "/content_description_" + wbTabNum + ".xml");
			FileWriter xmlfw = new FileWriter(xmlDescFile);
			xmlfw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
			xmlfw.write("<description>\n");
			xmlfw.write("\t<slides>\n");
			xmlfw.write("\t\t<slide page=\"1\" title=\"\" info=\"\" datafile=\""  + newfn + "\" type=\"image\">" + newfn + "</slide>\n");
			xmlfw.write("\t</slides>\n");
			xmlfw.write("</description>\n");
			xmlfw.close();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	public void setWorkingData(String roomid) {
		myDebug("setWorkingData");
		
	//	backupWorkingData(roomid); // backup the latest content
		
		String roomDir = new String(virtualDir + "/" + roomid);
		try {
			String[] cmd = new String[3];
			cmd[0] = "/bin/sh";
			cmd[1] = "-c";
			cmd[2] = "/bin/rm -rf " + roomDir + "/files/* && ";
			cmd[2] += "/bin/rm -rf " + roomDir + "/flv/* && ";
			cmd[2] += "/bin/rm -rf " + roomDir + "/data/* && ";
			cmd[2] += "/bin/rm -rf " + roomDir + "/wb_state && ";
			cmd[2] += "/bin/cp -a " + roomDir + "/orig/* " + roomDir + "/ ";
			myDebug(cmd[2]);
			Process p = Runtime.getRuntime().exec(cmd);	
			p.waitFor();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		clear_params();
	}

	private void backupWorkingData(String roomid) {
		myDebug("backupWorkingData");
		String roomDir = new String(virtualDir + "/" + roomid);
		try {
			String[] cmd = new String[3];
			cmd[0] = "/bin/sh";
			cmd[1] = "-c";
			cmd[2] = "cd " + roomDir + " && ";
			cmd[2] += "/bin/rm -rf backup/* && "; // delete existing data
			cmd[2] += "/bin/cp -a data files flv content_description.xml backup && ";
			cmd[2] += "/bin/rm -f backup/data/message.txt "; // delete chat message
			myDebug(cmd[2]);
			Process p = Runtime.getRuntime().exec(cmd);
			p.waitFor();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void restoreModifiedData(String roomid) {
		myDebug("restoreModifiedData");
		
		String roomDir = new String(virtualDir + "/" + roomid);
		try {
			String[] cmd = new String[3];
			cmd[0] = "/bin/sh";
			cmd[1] = "-c";
			cmd[2] = "/bin/cp -a " + roomDir + "/backup/* " + roomDir + "/ ";
			myDebug(cmd[2]);
			Process p = Runtime.getRuntime().exec(cmd);	
			p.waitFor();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private String genValidKey() {
		long ctime = new Date().getTime();
		
		return md5Encode(String.valueOf(ctime));
	}
	
	private String md5Encode(String text) {
		String result = "";
		
		try {
			MessageDigest m = MessageDigest.getInstance("MD5");
			m.update(text.getBytes("UTF8"));
			byte s[] = m.digest();
			for (int i = 0; i < s.length; i++) {
				result += Integer.toHexString((0x000000ff & s[i]) | 0xffffff00).substring(6);
			}
		}
		catch (Exception ex) {
			myDebug(ex.getMessage());
		}
		
		return result;
	}
	
	public void saveMessage(String login, String message, String color) throws IOException {
		myDebug("saveMessage");
		
		String fn = new String(virtualDir + "/" + currentContentID + "/data/message.txt");
		File fos = new File(fn);
		FileWriter fw = new FileWriter(fos, true); //true for append and false for overwrite
		Date date = new Date();
		SimpleDateFormat dt = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss.S");
		fw.write("[" + dt.format(date) + "] " + login + "#" + color + " : " + message + "\n");
		fw.close();
		fos = null;
	}
	
	public void setPresenterID(int pid, Boolean status) throws IOException {
		myDebug("setPresenterID pid = " + pid);
		currentPresenterID = pid;
		presenterStreamStatus = status ? 1 : 0 ;
		
		saveRoomState();
	//	genCurrentState();  // not needed anymore for lecture client
	}
	 
	private void genCurrentState() {
		myDebug("genCurrentState");
		
		long anno_timestamp = new Date().getTime();
		String roomDir = new String(virtualDir + "/" + currentContentID);
		try {
			// generate content file
			File xmlDescFile = new File(roomDir + "/current_state.xml");
			FileWriter xmlfw = new FileWriter(xmlDescFile);
			xmlfw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
			xmlfw.write("<data>\n");
			xmlfw.write("\t<streamid status=\"" + presenterStreamStatus + "\">" + currentPresenterID + "</streamid>\n");
			xmlfw.write("\t<content type=\"" + slide_type[currentPageNumber -1] + "\" annomode=\"" + currentLectureMode + "\" annotstamp=\"" + anno_timestamp + "\">" + slide_name[currentPageNumber -1] + "</content>\n");
			xmlfw.write("\t<predict type=\"" + slide_type[predictPageNumber -1] + "\">" + slide_name[predictPageNumber -1] + "</predict>\n");
			xmlfw.write("</data>\n");
			xmlfw.close();		
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

     	/**
     	 * SIP transport methods
     	 */

	private long stringToLongParser(String myString) {
       		NumberFormat numberFormat=NumberFormat.getNumberInstance();
   		Long myLongString = null;

		try {
        		myLongString = numberFormat.parse(myString.toString()).longValue();
   		} catch (ParseException ex) {
       			 // Handle exception
			myDebug("Cannot parse the string input...");
    		}

		return myLongString;
	}

    public void joinToConfCall(String number) {
		IConnection current = Red5.getConnectionLocal();
		String scope = Red5.getConnectionLocal().getScope().getName();
       	NumberFormat numberFormat=NumberFormat.getNumberInstance();
		Long roomnumber = null;
	
		try {
        	roomnumber = numberFormat.parse(scope.toString()).longValue();
			myDebug("Room Number OK!");
		} catch (ParseException ex) {
       		 // Handle exception
			myDebug("Cannot recognized the scope or roomnumber...");
    	}

        try {
			String sipNumber = getSipNumber(roomnumber);
            log.debug("asterisk -rx \"originate Local/" + number + "@rooms-out extension " + sipNumber + "@rooms-originate\"");
            myDebug("asterisk -rx originate Local" + number + "@rooms-out extension " + sipNumber + "@rooms-originate");
            Runtime.getRuntime().exec(new String[] { "asterisk", "-rx", "originate Local/" + number + "@rooms-out extension " + sipNumber + "@rooms-originate" });
		} catch (IOException e) {
            log.error("Executing asterisk originate error: ", e);
        }
    }

    // getSipNumber with argument [content_id]
    public String getSipNumber(Long roomnumber) {
    	String sipnumber = null;
    
    	try {
			
			connectDB();
			query = "select * from virtualroom ";
			query += "where roomnumber=? ";
			stmt = mySQLConn.prepareStatement(query);
			stmt.setLong(1, roomnumber);
			ResultSet rs = stmt.executeQuery();
			while (rs.next()) { 
				sipnumber = rs.getString("sipnumber");			
            			myDebug("SipNumber: " + rs.getString("sipnumber") + "; RoomNumber : " + roomnumber + "; ContentID: " + rs.getString("roomname"));
			}
      		stmt.close();
		}
		catch ( SQLException e ) {
			e.printStackTrace();
		}
    	
        if(sipnumber != null) {
        	return sipnumber;
        }
        return null;
    }

	public String getPublicSID() {
		myDebug("getPublicSID");
		IConnection conn = Red5.getConnectionLocal();
		
		return conn.getClient().getId();
	}	

	public long getBroadCastId() {
		myDebug("getPublicSID");
		IConnection conn = Red5.getConnectionLocal();

		String id = conn.getClient().getId();
		
		return stringToLongParser(id);
	}	

	public List<Integer>listRoomBroadcast() {
		myDebug("listRoomBroadcast");
		HashSet<Integer> broadcastList = new HashSet<Integer>();
		String roomnumber = null;
    	
		try {
			
			connectDB();
			query = "select roomnumber from virtualroom ";
			stmt = mySQLConn.prepareStatement(query);
			ResultSet rs = stmt.executeQuery();
			while (rs.next()) { 
				roomnumber = rs.getString("roomnumber");			
            			myDebug("room number : " + roomnumber);
				broadcastList.add(Integer.parseInt(roomnumber));
			}
            		stmt.close();
		}
		catch ( SQLException e ) {
			e.printStackTrace();
		}
		return new ArrayList<Integer>(broadcastList);
	
	}

	public void setSipTransport(Long room_id, String publicSID, String broadCastId) {
		myDebug("setSipTransport");
		myDebug("setSipTransport - Params:3 room=id:"+room_id+" publicSID:"+publicSID+" broadcastId:"+broadCastId);
	/*	IScope scope = Red5.getConnectionLocal().getScope();
		Set<IClient> clients = scope.getClients();
		Iterator<IClient> it = clients.iterator();
		
		if(clients.isEmpty()) {
			myDebug("client list is empty");
		}
		
		Object[] params = new Object[]{"SIP User",10,true,true,false,false,false,false,false,false};
		while(it.hasNext()) {
			IClient client = it.next();
			Set<IConnection> connset = client.getConnections();
			Iterator<IConnection> it_con = connset.iterator();
			
			while(it_con.hasNext()) {
				IConnection connection = it_con.next();
				if(connection instanceof IServiceCapableConnection) {
					IServiceCapableConnection sc = (IServiceCapableConnection)connection;
					sc.invoke("updateUserList", params, this);
					myDebug("setSipTransport - invoke updateUserList - Conn: " + connection);
				} else {
					myDebug("This connection is not IServiceCapableConnection");
				}
			}
		}
		*/
		
	/*	IScope scope = Red5.getConnectionLocal().getScope();
		Object[] params = new Object[]{"SIP User",10,true,true,false,false,false,false,false,false};
		
		for (Set<IConnection> connection : scope.getConnections()) {
			try {
    			for (IConnection tmpConn : connection) {
    				if (tmpConn instanceof IServiceCapableConnection) {
    					IServiceCapableConnection sc = (IServiceCapableConnection) tmpConn;
    					sc.invoke("updateUserList", params);
						myDebug("setSipTransport - invoke updateUserList - Conn: " + tmpConn);
    				}				
    			}
			} catch (NoSuchElementException e) {
				log.error("Previous scope connection is unavailable", e);
			}
		}
	*/

		//ServiceUtils serviceUtils = new org.red5.server.api.service.ServiceUtils();
		
		Object[] params = new Object[]{"SIP User",10,true,true,false,false,false,false,false,false};
//		serviceUtils.invokeOnAllConnections("updateUserList", params);
//		RTMPConnection.invoke("updateUserList", params);	
		
	}
	
	public int updateSipTransport() {
		myDebug("updateSipTransport");
		
		// for testing
		Integer count = 0;
		return count;
	}
	
	public List<Long> getActiveRoomIds() {
		List<Long> result = new ArrayList<Long>(null);
		String roomname = null;
		
		try {
			
			connectDB();
			query = "select roomname from virtualroom ";
			query += "where roomname!=? ";
			stmt = mySQLConn.prepareStatement(query);
			stmt.setString(1, "0");
			ResultSet rs = stmt.executeQuery();
			while (rs.next()) { 
				roomname = rs.getString("roomname");	
				myDebug("getActiveRoomIds - Active SIP Rooms: " + roomname);	
        		result.add(stringToLongParser(roomname));
			}
            stmt.close();
		}
		catch ( SQLException e ) {
			e.printStackTrace();
			myDebug("Error - getActiveRoomIds - Active SIP Rooms ");	
		}
		return result;
		
	}
	
	public ArrayList<String> getSharedFiles(String roomid){
		ArrayList<String> sharedFilesList = new ArrayList<String>(); 
	
		myDebug("getSharedFiles");
		String roomDir;
		roomDir = new String(virtualDir + "/" + roomid + "/shared");
		myDebug(roomDir);
				
		FilenameFilter filter = null;
		File tmpDir = new File(roomDir);
		if (tmpDir.exists()) {
			String[] sharedFiles = tmpDir.list();
			Arrays.sort(sharedFiles);
			for (int i=0; i<sharedFiles.length; i++) {
				sharedFilesList.add(sharedFiles[i]);
				myDebug(sharedFiles[i].toString());
			}
		}
		
		return sharedFilesList;
	} 
	
	public void deleteSharedFile(String roomid, String file){
		myDebug("deleteSharedFile : " + file);
				
		String roomDir = new String(virtualDir + "/" + roomid + "/shared");
		String fileToDelete = new String(roomDir + "/" + file);
		try {
			String[] cmd = new String[3];
			cmd[0] = "/bin/sh";
			cmd[1] = "-c";
			cmd[2] = "/bin/rm -f " +  fileToDelete; // delete shared file
			myDebug(cmd[2]);
			Process p = Runtime.getRuntime().exec(cmd);
			p.waitFor();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
		
	public void deleteAllSharedFiles(String roomid){
		myDebug("deleteAllSharedFile for content :" + roomid);
				
		String roomDir = new String(virtualDir + "/" + roomid + "/shared");

		try {
			String[] cmd = new String[3];
			cmd[0] = "/bin/sh";					
			cmd[1] = "-c";
			cmd[2] = "cd " + roomDir + " && ";						
			cmd[2] += "rm -f *.* && "; // delete all shared files
			cmd[2] += "touch index.html";
			myDebug(cmd[2]);
			Process p = Runtime.getRuntime().exec(cmd);
			p.waitFor();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

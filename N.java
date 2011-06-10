//run "java -Dsun.java2d.noddraw=true N"
/*

v1.1
----
 - restart level, fixes particle count
 - add touch bubble fx
 - resize font size

//7620 - 7468, 7455


do NOT optmize code like this

double freq = twoPi*freqM[N];
				if (N==1) {
				...
							Math.sin(freq/1.8) * Math.sin((freq/1.8)/(freq2/1.8) ) +							
				}
				if (N==2) {
					s = (short)( 6000*(sampLength-scale)/sampLength*
							((Math.sin(freq)+
									//Math.sin(freq/1.8) +
									Math.sin(freq/1.8) +
									Math.sin(freq/1.5))/3.0) );
				}

->
double freq = twoPi*freqM[N];
double f18 = freq/1.8;
...
Math.sin(f18) * ...


-> compression ratio is worse!

 */ 

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.Random;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import javax.swing.JFrame;


public class N extends JFrame implements Runnable {

	//mouseX, mouseY, retries, restartKey
	private static int globalEvent[] = new int[4];	
	private static byte[][] audioData = new byte[3][];

	int selectedSound;

	public void processEvent(AWTEvent awtevent){
		int i = awtevent.getID();
		if (i == 201)
			System.exit(0);

		if(i == 501 || i == 502) {        	
			if ( ((MouseEvent)awtevent).getButton() == MouseEvent.BUTTON1) {        		
				globalEvent[0] = ((MouseEvent)awtevent).getX();
				globalEvent[1] = ((MouseEvent)awtevent).getY();
			}
		}
		//keypress
		if(i == 401 || i == 402) {      
			if (((KeyEvent)awtevent).getKeyCode() == KeyEvent.VK_R) {
				globalEvent[3] = 1;    		 
			}
			if (((KeyEvent)awtevent).getKeyCode() == KeyEvent.VK_F12) {
				globalEvent[2] = 3;
			}
		}
	}

	//dummy constructor for multithread sound 
	public N(int i) {
		selectedSound = i;		
	}

	public N() {		
		int gameStatus;
		int level=0;
		int scoreTotal=0;
		int scoreLevel=0;					
 
		double[] x,y,dir;
		int[] pStatus, direction, radius, maxRadius, pulse, lifetime, pFrame, explosion;
		Color[] color=null;
		pStatus=direction=radius=maxRadius=pulse=lifetime=pFrame=explosion=null;
		x=y=dir=null;		
		int levelUp=0;
		int decRetries=0;
		int updateTotalScore=0;
		boolean updateGame=true;

		final int DISPLAY_TIME_IN_MS = 2000;

		final int MAX_LEVEL = 19;	

		int lvlNumberOfParticles=10;
		int lvlTimeOut=70;
		int lvlNeededScore=1;

		//level 0: 10% trefferquote, level max: 70% trefferquote
		final float QUOTE_BEGIN = 0.1F; //10;  // erstes level ben�tigt 10% getroffene
		final float QUOTE_END   = 0.7F; //70;  // erstes level ben�tigt 10% getroffene
		final float QUOTE_PER_LEVEL = (QUOTE_END-QUOTE_BEGIN)/(MAX_LEVEL+1);  //z.B. 2% bessere trefferquote pro level

		final int STATUS_DISPLAY_LEVEL = 0;
		final int STATUS_WAIT_ON_INITIAL_CLICK = 10;
		final int STATUS_GAME_IS_RUNNING = 20;		
		final int STATUS_FADE_OUT_CURRENT_LEVEL_START = 30;
		final int STATUS_FADE_OUT_CURRENT_LEVEL_END = 31;
		final int STATUS_FINISHED = 40;
		final int STATUS_GAME_OVER = 50;

		final int STATE_NORMAL = 0;
		final int STATE_BLOWNUP = 10;
		final int STATE_SHRINK = 20;
		final int STATE_DELETE = 30;
		final int STATE_FADE_AWAY = 40;		

		final int RADIUS = 50;	    
		final int XSIZE = 640;
		final int YSIZE = 480;

		final int MOUSE_X_POS = 0;
		final int MOUSE_Y_POS = 1;
		final int RETRIES = 2;
		final int RESTART_KEY = 3;		

//		setSize(new Dimension(640+getInsets().right*getInsets().left,480+getInsets().top+getInsets().bottom));
		setSize(XSIZE, YSIZE);
//		setLocation(100,100);				
		setResizable(false);		
		setTitle("jm00//neophob.com//vantage.ch");
		enableEvents(511L);
		setVisible(true);	

/*		Graphics2D g2,g2BgImage;
		Graphics2D gfx = (Graphics2D)this.getGraphics();
		VolatileImage g = createVolatileImage(XSIZE, YSIZE);		
		(g2 = (Graphics2D)g.getGraphics()).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);		

		VolatileImage bgImage = createVolatileImage(XSIZE, YSIZE);	
		g2BgImage = (Graphics2D)bgImage.getGraphics();
*/
		Color bg = new Color(35,35,35,255);
		//XXX:TO minimize
//		Font bgFont = new Font("Dialog", 0, 66 * Toolkit.getDefaultToolkit().getScreenResolution() / 72);

		/*
		 * INIT GAMECONTROLLER
		 */
		globalEvent[RETRIES] = 3;
		gameStatus = STATUS_DISPLAY_LEVEL;		

		/*
		 * CREATE SOUND
		 */

		audioData[0]  = new byte[3000*4];
		audioData[1]  = new byte[16000*4];
		audioData[2]  = new byte[3000*4];

		for (int N=0; N<3; N++) {
			ShortBuffer shortBuffer = ByteBuffer.wrap(audioData[N]).asShortBuffer();
			int sampLength = audioData[N].length/2;  //2 Bytes per Samples = 1 signed short
			for(int i=0; i<sampLength; i++){
				//The value of scale controls the rate of decay - large scale, fast decay.
				//double twoPi = 6.2831853 * (i/16000.0F);
				float twoPi = 0.00039269908125F * i;
//				short s=0;

				//boing
				if (N==0) {
					float scale = 2*i;
					if(scale > sampLength) 
						scale = sampLength;

					float freq = 699.0F * twoPi;
					//s =
					shortBuffer.put(
							(short)( 6000*(sampLength-scale)/sampLength*
									((Math.sin(freq)+
											Math.sin(freq/1.8F) +
											Math.sin(freq/1.5F))/3.0) )
					);
				}

				//level sound
				if (N==1) {
					float scale = i*1.3F;
					if(scale > sampLength) 
						scale = sampLength;

					float freq  = 369.99F * twoPi;
					float freq2 = 349.23F * twoPi;

					//s =
					shortBuffer.put(
							(short)( 16000*(sampLength-scale)/sampLength*  (
									Math.sin(freq) * Math.sin((freq)/ freq2) +
									Math.sin(freq/1.8F) * Math.sin((freq/1.8F)/(freq2/1.8F) ) +
									Math.sin(freq/1.5F) * Math.sin((freq/1.5F)/(freq2/1.5F) )
							)/3.0 )
					);
				}

				//blop
				if (N==2) {
					float scale = 2*i;
					if(scale > sampLength) 
						scale = sampLength;

					float freq  = 19.0F * twoPi;
					float freq2 = 399.0F * twoPi;					
					//s =
					shortBuffer.put(
							(short)( 6000F*(sampLength-scale)/sampLength*
									((Math.sin(freq*freq)+Math.sin(freq2)/2) ))
					);
				}

				//shortBuffer.put(s);				
			}		
		}


		/*
		* Create graphics contexts. The off image is drawn to the JFrame,
		* making it possible to do pixel operations for the graphics. Also,
		* drawing to g (the buffer's graphics context) will make sure that
		* what you draw is double-buffered.
		*/
		Image off = createImage(XSIZE, YSIZE);
		Image g2BgImage= createImage(XSIZE, YSIZE);


		/*
		 * INIT PARTICLELOGIC
		 */		
		Random rand = new Random();
		/*
		 * ___________________ MAINLOOP ___________________
		 * 
		 */		 

		long t1 = System.currentTimeMillis();		

		while(true) {

			long fps1 = System.currentTimeMillis();
			Graphics2D gFrame = (Graphics2D)getGraphics();			
			Graphics2D g = (Graphics2D)off.getGraphics();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			
			if (gameStatus == STATUS_FADE_OUT_CURRENT_LEVEL_END) {
				t1 = System.currentTimeMillis();
				gameStatus = STATUS_DISPLAY_LEVEL;				
				updateGame=true;		

				//update statistics
				scoreLevel = 0;
				scoreTotal+=updateTotalScore;
				updateTotalScore=0;

				//new level
				if (levelUp==1) {
					level++;
					lvlNumberOfParticles+=2;
					if (level<9)
						lvlNumberOfParticles+=2;
					if (level>12)
						lvlTimeOut-=2;
					lvlNeededScore = (int)(lvlNumberOfParticles*(QUOTE_PER_LEVEL*(level+4)+QUOTE_BEGIN)+.5);
				}
				//restart game
				if (levelUp==16) {
					level=0;
					globalEvent[RETRIES] = 3;          
					scoreTotal = 0;
					scoreLevel = 0;
					lvlNumberOfParticles=10;
					lvlTimeOut=70;
					lvlNeededScore=1;
				}
				levelUp=0;

				//huh? maybe error?
				if (decRetries!=0)
					globalEvent[RETRIES]--;
				decRetries=0;

				//changed
				globalEvent[MOUSE_X_POS] = globalEvent[MOUSE_Y_POS] = 0;
			} 

			if (updateGame) {								
				x = new double[ lvlNumberOfParticles ];
				y = new double[ lvlNumberOfParticles ];
				dir = new double[ lvlNumberOfParticles ];
				pStatus = new int[ lvlNumberOfParticles ];
				direction = new int[ lvlNumberOfParticles ];
				radius = new int[ lvlNumberOfParticles ];
				maxRadius = new int[ lvlNumberOfParticles ];
				pulse = new int[ lvlNumberOfParticles ];
				lifetime = new int[ lvlNumberOfParticles ];
				pFrame = new int[ lvlNumberOfParticles ];
				color = new Color[ lvlNumberOfParticles ];
				explosion = new int[ lvlNumberOfParticles ];

				for(int i=0; i<lvlNumberOfParticles; i++) {
					x[i] = RADIUS+rand.nextInt(XSIZE-RADIUS*2);
					y[i] = RADIUS+rand.nextInt(YSIZE-RADIUS*2);

					pFrame[i] = rand.nextInt(360);
					dir[i] = pFrame[i]*0.017453F;
					color[i] = new Color(128, 154, rand.nextInt(256), 128);
					pulse[i] = 0;
					direction[i] = 1;
					pStatus[i] = STATE_NORMAL;
					lifetime[i] = lvlTimeOut;
					maxRadius[i] = 60;					
					radius[i] = rand.nextInt(32);
					explosion[i] = 255;

					int spezialFunktion = pFrame[i]%12;

					if (spezialFunktion==3) {
						maxRadius[i] = 120;
						//gelbe farbe - DOUBLE RADIUS
						color[i] = new Color(222, 222, 75, 128);
					}

					if (spezialFunktion==8) {
						lifetime[i] *=2;
						//weisse farbe - DOUBLE_LIFETIME
						color[i] = new Color(222, 222, 222, 128);
					}

					if (spezialFunktion==5) {
						maxRadius[i] = 36;
						//rote farbe - HALF RADIUS
						color[i] = new Color(252, 154, 75, 128);						
					}
				}

				//create background image
				Graphics2D gX = (Graphics2D)g2BgImage.getGraphics();
				gX.setColor( new Color(0,0,0,255) );
				gX.fillRect(0, 0, XSIZE, YSIZE);					
				gX.setColor( new Color(64,64,64,64) );
				for (int ixx=0; ixx<36; ixx++) {
					int bgx1=rand.nextInt(XSIZE-10);
					int bgy1=rand.nextInt(YSIZE-10);						
					gX.fillRoundRect(bgx1, rand.nextInt(XSIZE-bgx1), bgy1, rand.nextInt(YSIZE-bgy1), 16, 16);					
				}		
				
/*				g2BgImage.setColor( new Color(0,0,0,255) );
				g2BgImage.fillRect(0, 0, XSIZE, YSIZE);					
				g2BgImage.setColor( new Color(64,64,64,64) );
				for (int ixx=0; ixx<36; ixx++) {
					int bgx1=rand.nextInt(XSIZE-10);
					int bgy1=rand.nextInt(YSIZE-10);						
					g2BgImage.fillRoundRect(bgx1, rand.nextInt(XSIZE-bgx1), bgy1, rand.nextInt(YSIZE-bgy1), 16, 16);					
				}		
*/
				updateGame = false;
				//Play Level sound
				new Thread(new N(1)).start();
			}     

			if (gameStatus == STATUS_WAIT_ON_INITIAL_CLICK) {

				for(int i=0 ; i<lvlNumberOfParticles; i++) {
					double dx = x[i]-globalEvent[MOUSE_X_POS];
					double dy = y[i]-globalEvent[MOUSE_Y_POS];

					// 8 = pixel radius for click
					if ((int)Math.sqrt(dx*dx + dy*dy) <= ( 8+(pulse[i]+radius[i])/2 )) {						
						pStatus[i] = STATE_BLOWNUP;
						gameStatus = STATUS_GAME_IS_RUNNING;						
						explosion[i] = 0;
						//play sound						
						new Thread(new N(2)).start();
					}
				}

				globalEvent[MOUSE_X_POS]=-10000;
				globalEvent[MOUSE_Y_POS]=10000;		
				globalEvent[RESTART_KEY]=0;
			} // end STATUS_WAIT_ON_INITIAL_CLICK

			//level ist fertig, bestehende partikel werden ausgefaded
			if (gameStatus == STATUS_FADE_OUT_CURRENT_LEVEL_START) {
				//check if particles are left...
				int cnt=0;
				for(int i=0 ; i<lvlNumberOfParticles; i++) {
					if (pStatus[i] == STATE_NORMAL) {
						pStatus[i] = STATE_FADE_AWAY;
					}
					if (pStatus[i] == STATE_FADE_AWAY && radius[i] > 0) {
						cnt++;
					}
				}

				//wenn fertig ausgefaded ist, kurz warten
				if (cnt==0) {
					try { 
						Thread.sleep(400);
					} catch(Exception e) {
						//System.out.println("THREAD ERROR: "+e.getMessage());
					}
					gameStatus = STATUS_FADE_OUT_CURRENT_LEVEL_END;
				}
			} //end STATUS_FADE_OUT_CURRENT_LEVEL_START


			/*
			 * GC UPDATE GAME
			 */			

			if (gameStatus == STATUS_DISPLAY_LEVEL) {
				if (System.currentTimeMillis()-t1>DISPLAY_TIME_IN_MS) {
					gameStatus = STATUS_WAIT_ON_INITIAL_CLICK;					
				}
			}

			if ((gameStatus == STATUS_GAME_OVER || gameStatus == STATUS_FINISHED) && globalEvent[RESTART_KEY]==1) {
				globalEvent[RESTART_KEY]=0;
				//restart game
				levelUp = 16;
				gameStatus = STATUS_FADE_OUT_CURRENT_LEVEL_START;
			}

//			game running start
			if (gameStatus == STATUS_GAME_IS_RUNNING) {
				//ein sprite wurde bereits selektiert

				int blownUp=0;
				int deleted=0;
				int shrink=0;		

				//für alle partikel
				for(int i=0; i<lvlNumberOfParticles; i++) {
					//welche schon aufgebläht sind (oder am shrinken sind)
					if (pStatus[i] == STATE_BLOWNUP || pStatus[i] == STATE_SHRINK) {						
						if (pStatus[i] == STATE_BLOWNUP)
							blownUp++;
						else
							shrink++;

						//JoGa Bug, wenn diese definitionen im Loop sind!
						//double dx,dy;
						//jetzt wird verglichen
						for(int j=0; j<lvlNumberOfParticles; j++) {

							if (pStatus[j] == STATE_NORMAL) {
								//dx = x[j]-x[i];
								//dy = y[j]-y[i];

								//check if the particles touches another one
								//if ((int)Math.sqrt(dx*dx + dy*dy) <= ( (pulse[i]+radius[i]+pulse[j]+radius[j])/2 )) {
								if ((int)Math.sqrt(((x[j]-x[i])*(x[j]-x[i])) + ((y[j]-y[i])*(y[j]-y[i]))) <= ( (pulse[i]+radius[i]+pulse[j]+radius[j])/2 )) {                        
									//play sound
									int snd=2;
									if (maxRadius[j]==120 || maxRadius[j]==36 || lifetime[j]==lvlTimeOut*2)
										snd=0;
									new Thread(new N(snd)).start();
									//update particles
									pStatus[j] = STATE_BLOWNUP;
									explosion[j] = 0;
								}
							}
						}

					}
					if (pStatus[i] == STATE_DELETE) 
						deleted++;
				}

				scoreLevel = deleted+blownUp+shrink;				
				if ( blownUp == 0 && deleted>0 && shrink==0) {

					if (scoreLevel >= lvlNeededScore) {              
						updateTotalScore = scoreLevel;
						if (level == MAX_LEVEL) {
							gameStatus = STATUS_FINISHED;
						} else {																					
							//level ist fertig, verbleibende partikel ausfaden
							gameStatus = STATUS_FADE_OUT_CURRENT_LEVEL_START;
							levelUp=1;
						}
					} else {
						if (globalEvent[RETRIES]>1) {
							decRetries=1;
							//level ist nicht beended, verbleibende partikel ausfaden
							gameStatus = STATUS_FADE_OUT_CURRENT_LEVEL_START;
						} else {
							gameStatus = STATUS_GAME_OVER;
							globalEvent[RETRIES] = 0;							
						}
					}
				}				
			} //end game is running

			/*
			 * set background
			 */			
			Color dummy = new Color(25,75,55,255);//bgDone;
			if (lvlNeededScore > scoreLevel) {
				dummy = new Color(25,25,55,255);//bgNormal;
			}

			g.setColor(			
					bg = new Color(  
							//bg.getRed() - (( bg.getRed() - dummy.getRed() ) >> 4),
							(15*bg.getRed()+dummy.getRed()) >> 4,
							//bg.getGreen() - (( bg.getGreen() - dummy.getGreen() ) >> 4),
							(15*bg.getGreen()+dummy.getGreen()) >> 4,
							//bg.getBlue() - (( bg.getBlue() - dummy.getBlue() ) >> 4),
							(15*bg.getBlue()+dummy.getBlue()) >> 4,
							128)
			);						
			
			
			g.drawImage(g2BgImage, 0, 0, null);
			g.fillRect(0,0,XSIZE, YSIZE);

			g.setColor( new Color(0,0,0,92) );			
			//g.setFont( bgFont );
			//0.916 = 66 (fontsize) / 72dpi
			g.setFont( new Font("Dialog", 0, (int)(0.916f * Toolkit.getDefaultToolkit().getScreenResolution()) ));
			g.drawString("points "+(scoreLevel)+"/"+lvlNeededScore, 0, 108);
			g.drawString("score "+(scoreLevel+scoreTotal), 55, 310);
			g.drawString("level "+(level+1)+"/"+(MAX_LEVEL+1), 33, 390);
			g.drawString("retries "+(globalEvent[RETRIES]), 210, 470);

			for(int i=0 ; i<lvlNumberOfParticles; i++) {										
				//changed
				if (pStatus[i] == STATE_BLOWNUP)
					pulse[i] = (int)(Math.sin((pFrame[i]/2)%360)*4);

				//SPEED = 1.5
				pFrame[i]++;
				if (pStatus[i] == STATE_NORMAL) {
					double cx, cy;
					if (radius[i] < 14)
						radius[i]++;

					if(direction[i] == 0) {
						x[i] += 1.5F * Math.cos(dir[i]);
						y[i] += 1.5F * Math.sin(dir[i]);
					} else {
						//int dirRad = direction[i] * RADIUS;
						cx = x[i] - direction[i] * RADIUS * Math.sin(dir[i]);
						cy = y[i] + direction[i] * RADIUS * Math.cos(dir[i]);
						dir[i] += direction[i] * 1.5/RADIUS;
						x[i] = cx + direction[i] * RADIUS * Math.sin(dir[i]);
						y[i] = cy - direction[i] * RADIUS * Math.cos(dir[i]);
					}

					//int RANDOMNESS = 5;
					if(rand.nextInt(5) == 0) 
						direction[i] = rand.nextInt(3) - 1;									

					// Avoid walls
					cx = x[i] - RADIUS * Math.sin(dir[i]);
					cy = y[i] + RADIUS * Math.cos(dir[i]);

					//je nachdem ckomprimiert besser
					/*for (int n=-1; n<2; n+=2) {
						if(cx<RADIUS+20 || cx>XSIZE-RADIUS-20 || cy<RADIUS+40 || cy>YSIZE-RADIUS-20) direction[i] = n;
						cx = x[i] + rsin;
						cy = y[i] - rcos;						
					}  */

					if(cx<RADIUS+20 || cx>XSIZE-RADIUS-20 || cy<RADIUS+40 || cy>YSIZE-RADIUS-20) direction[i] = -1;
					cx = x[i] + RADIUS * Math.sin(dir[i]);
					cy = y[i] - RADIUS * Math.cos(dir[i]);						
					if(cx<RADIUS+20 || cx>XSIZE-RADIUS-20 || cy<RADIUS+40 || cy>YSIZE-RADIUS-20) direction[i] = 1;
				} 

				if (pStatus[i]  == STATE_BLOWNUP) {
					lifetime[i]--;
					if (lifetime[i] <= 0)
						pStatus[i]  = STATE_SHRINK;

					if (radius[i] < maxRadius[i])
						radius[i]+=4;
				}

				if (pStatus[i]  == STATE_SHRINK) {
					radius[i] -= 4;
					if (radius[i] <= 3) {
						pStatus[i]  = STATE_DELETE;
						radius[i] = 0;
					}
				}

				if (pStatus[i]  == STATE_FADE_AWAY) {					
					if (radius[i] > 0) 					
						radius[i]--;
				}		


				//drawit
				g.setColor(color[i]);

				int rad=radius[i]+pulse[i];

				//zeichne einen kleinen punkt, in richtung der bewegungn
				if (pStatus[i] == STATE_NORMAL) {										
					g.fillRect( (int)(x[i]+Math.cos(dir[i])*(rad/2)+.5F), (int)(y[i]+Math.sin(dir[i])* (rad/2)+.5F), 3, 3);
				}

				//nichts zeichnen, wenn das partikel leer ist
				if (pStatus[i] != STATE_DELETE) {					
					g.fillOval((int)x[i]-rad/2, (int)y[i]-rad/2, rad, rad);
					g.drawOval((int)x[i]-rad/2, (int)y[i]-rad/2, rad, rad);

					if (explosion[i] < 100) {
//						int size = explosion[i] + rad;
//						g2.setColor(new Color(255-size,230,255-size,32));
//						g2.fillOval((int)x[i]-size/2, (int)y[i]-size/2, size, size);

						g.setColor(new Color(255-(explosion[i] + rad),230,255-(explosion[i] + rad),32));
						g.fillOval((int)x[i]-(explosion[i] + rad)/2, (int)y[i]-(explosion[i] + rad)/2, explosion[i] + rad, explosion[i] + rad);
						explosion[i] += 6;
					}
				}
			}

			/*
			 * PAINT HUD
			 */
			String str=null;

//			draw messages			
			final String RESTART_GAME = " points! Press R to restart game";
			//dummy=new Color(180, 240, 120, 120);
			g.setColor( new Color(182, 240, 120, 120) );
			if (gameStatus == STATUS_DISPLAY_LEVEL) {
				str = "Level: "+(level+1);
			} 
			if (gameStatus == STATUS_FINISHED) {							
				str = "You did it, "+(scoreLevel+scoreTotal)+RESTART_GAME;
			}				
			if (gameStatus == STATUS_GAME_OVER) {							
				str = "Failed, "+(scoreLevel+scoreTotal)+RESTART_GAME;
				//ok green is red...
				//dummy =new Color(240, 180, 120, 120);
				g.setColor( new Color(240, 180, 120, 120) );
			} 

			if (str!=null) {
				Font f = new Font("Dialog", 0, 25);
				g.setFont(f);
				FontMetrics myFontMetrics = g.getFontMetrics(f);
				int stringWidth=myFontMetrics.stringWidth(str)/2;
				int stringHeight=myFontMetrics.getHeight()/2;

				g.fillRoundRect(280-stringWidth, 230-stringHeight, 2*stringWidth+80, 2*stringHeight+40, 16, 16);
				g.drawRoundRect(280-stringWidth, 230-stringHeight, 2*stringWidth+80, 2*stringHeight+40, 16, 16);

				g.setColor( new Color(25,25,55, 192) );
				g.drawString(str, 320 - stringWidth, 275 - stringHeight);
			}
			/*
			if (gameStatus == STATUS_WAIT_ON_INITIAL_CLICK) {
				g2.setColor(new Color(128,128,128,64));
				g2.fillOval(globalEvent[4]-8, globalEvent[5]-8, 16, 16);
			}
			 */
						
			//getGraphics().drawImage(g, 0, 0, null);
//			gfx.drawImage(g, 0, 0, null);
			
			gFrame.drawImage(off,0,0,null);

			g.dispose();
			gFrame.dispose();

			//this.fpsTime = 1000 / targetFPS; -> 25fps=40,
			// 15 fps -> 66
			// 25 fps -> 40
			// 33 fps -> 30
			int frameDuration = (int)(System.currentTimeMillis() - fps1);
			//System.out.println("ms for a frame: "+frameDuration);
			if (frameDuration < 30) {				
				try { 
					//System.out.println("ms for a frame: "+frameDuration+", Sleep: "+(30-frameDuration));
					Thread.sleep(30 - frameDuration); 
				} catch(Exception e) {
					//System.out.println(e.getMessage());				
				}
			}

		}
	}


	public void run(){
//		if (sndError < 10)
		try{
			int i;
			byte playBuffer[] = new byte[8192];
			// ripped from here: http://www.developer.com/java/other/article.php/2226701
			AudioFormat af = new AudioFormat(16000.0F,16,1,true,true); //1 channel sound
			SourceDataLine sdl = AudioSystem.getSourceDataLine(af);
			sdl = AudioSystem.getSourceDataLine(af);
			sdl.open(af);
			sdl.start();

			AudioInputStream audioInputStream=
				new AudioInputStream( new ByteArrayInputStream(audioData[selectedSound]), af, audioData[selectedSound].length/af.getFrameSize() );

			while((i = audioInputStream.read( playBuffer, 0, playBuffer.length)) != -1) {
				if(i > 0){
					sdl.write(playBuffer, 0, i);
				}                           
			}

			sdl.drain();
			sdl.stop();
			sdl.close();            
		} catch (Exception e) {
			//System.out.println(e.getMessage());
			//e.printStackTrace();
//			sndError++;
		}
	}


	public static void main(String args[]) 
	{
		new N();
	}

}


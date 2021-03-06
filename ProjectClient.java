package myProject;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;
public class ProjectClient
{
	public static void main(String[] args) throws Exception
	{
		final int SERVER_PORT_NUM = 9959;	//Server port number : this should be the same port as the one server is listening on.	
		int TIME_OUT_VALUE = 1000;	//Initial time-out value in milliseconds
		// creating the IP address object for the server machine
		// Method 1: loop back the request to the same machine
		InetAddress serverIP = InetAddress.getLocalHost();	//
		// Method 2: providing the server's IP address
		//byte[] serverIpAddress = { 129-256, 2, 90, 72 }; // corresponds to 129.2.90.72
		//InetAddress server = InetAddress.getByAddress(serverIpAddress);
		//Method 3: providing the server's host name//String serverHostName = "<server host name comes here>";
		//InetAddress server = InetAddress.getByName(serverHostName);
				
		int randomMeasurementID = getMeasurementID();		//Get a random measurement ID from the data file		
		//Generating a random request ID from 0 to 0xFFFF(65535)
		//Limiting this request ID to 16-bit unsigned integer by logical AND with 0xFFFF
		int requestID = (int) (Math.random()*65536) & 0xFFFF;
		int timeOutIteration=0;		//Initializing the time-out counter to '0'
		// Creating the UDP packet to be sent in the form of string		
		String packetToSend = "<request>\n\t<id>"+Integer.toString(requestID)+"</id>\n\t<measurement>"+Integer.toString(randomMeasurementID)+"</measurement>\n</request>";
		packetToSend+=integrityCheck(packetToSend);	//Appending the checksum value to the UDP packet	
				
		while(true)		//Running an infinite loop until terminated by user
		{
			System.out.println("The data sent to server is:\n"+packetToSend);	//Displaying the UDP packet to be sent
			byte[] sentMessage = packetToSend.getBytes();	//Converting the UDP packet from string to byte array
			DatagramPacket sentPacket = new DatagramPacket(sentMessage, sentMessage.length,	serverIP, SERVER_PORT_NUM);	//Initializing the UDP datagram packet to be sent
			DatagramSocket clientSocket = new DatagramSocket();		//Initializing the UDP Datagram socket to send the packet
			System.out.print("\nSending the request to the server...");	
			clientSocket.send(sentPacket);		//sending the UDP packet to the server
			clientSocket.setSoTimeout(TIME_OUT_VALUE);	//Setting the time-out for the socket
			
			byte[] receivedMessage = new byte[512];		//Creating the receiving byte array for the UDP packet
			DatagramPacket receivedPacket = new DatagramPacket(receivedMessage,	receivedMessage.length);	//Creating the receiving UDP packet
			//Receiving the server's response
			try
			{
				System.out.print("\nReceiving the response from the server...");
				clientSocket.receive(receivedPacket);	//Receiving the UDP packet; Time-out timer starts ticking here 
				requestID = (int) (Math.random()*65536) & 0xFFFF;		//Creating a new request ID for the next UDP packet to be sent				
				TIME_OUT_VALUE=1000;	//Resetting the timer value to 1000 ms after successful reception
				timeOutIteration=0;	//Resetting the time-out counter to 0 after successful reception
				
				String receievdMsg = new String(receivedPacket.getData());		//Converting the received bytes to string
				System.out.println("Received packet is:\n"+receievdMsg);		//Displaying the received bytes
				String tempSplit[] = new String[2];		//Creating a string array for string splitting purpose
				tempSplit = receievdMsg.split("</response>");	//Splitting the received bytes to get checksum value
				
				//Verifying the integrity check value by calculation and comparison
				if(integrityCheck(tempSplit[0]+"</response>").equals(tempSplit[1].trim()))	
				{
					tempSplit = tempSplit[0].split("<code>");		//Splitting the previous response to get 'code' value
					tempSplit = tempSplit[1].split("</code>");
					
					//Identifying and analyzing the code value in response
					if(tempSplit[0].equals("1"))
					{
						//Code 1: Integrity check failure in the request
						System.out.println("Error: integrity check failure. The request has one or more bit errors.\nDo you want to resend the packet(Y/N): ");
						Scanner myScanner = new Scanner(System.in);		//Getting user input on whether to re-send UDP packet
						String userInput = myScanner.next();
						if((userInput.toUpperCase()).equals("N"))
							randomMeasurementID = getMeasurementID();			//Getting new random measurement ID for next UDP packet to send			
					}//if
					else
					{
						if(tempSplit[0].equals("0"))
						{
							//Code 0: Successful transmission of request and Reception of response accordingly
							tempSplit=tempSplit[1].split("<value>");		//Splitting the string to get Temperature value for the measurement ID
							tempSplit=tempSplit[1].split("</value>");	
							System.out.println("Temperature value: "+tempSplit[0]+" F");		//Displaying the temperature value in Fahrenheit						
						}//if					
						else if((tempSplit[0]).equals("2"))	//Code 2: Incorrect syntax of the request UDP packet
							System.out.println("Error: malformed request. The syntax of the request message is not correct");
						else if((tempSplit[0]).equals("3"))	//Code 3: Non-existent measurement ID in the data file
								System.out.println("Error: non-existent measurement. The measurement with the requested measurement ID does not exist.");
						randomMeasurementID = getMeasurementID();		//Getting new random measurement ID for next UDP packet to send
					}//else
				}//if
				//Constructing the next UDP packet to be sent in the form of string
				packetToSend = "<request>\n\t<id>"+Integer.toString(requestID)+"</id>\n\t<measurement>"+Integer.toString(randomMeasurementID)+"</measurement>\n</request>";
				packetToSend+=integrityCheck(packetToSend);		//Appending the checksum value to the UDP packet	 
			}//try
			catch(InterruptedIOException e)
			{	// timeout - timer expired before receiving the response from the server				
				timeOutIteration++;		//Incrementing the time-out counter by one
				System.out.println("\n"+timeOutIteration+". Time-out experienced");
				if(timeOutIteration==4)		//Checking for occurrence of four time-outs
				{
					System.out.println("Communication Failure\nException message: " + e.getMessage());	//Declaring communication failure message
					randomMeasurementID = getMeasurementID();		//Getting new random measurement ID for next UDP packet to send
					requestID = (int) (Math.random()*65536) & 0xFFFF;		//Creating a new request ID for the next UDP packet to be sent
					packetToSend = "<request>\n\t<id>"+Integer.toString(requestID)+"</id>\n\t<measurement>"+Integer.toString(randomMeasurementID)+"</measurement>\n</request>";
					packetToSend+=integrityCheck(packetToSend);		//Appending the checksum value to the UDP packet	
				}
				else
				{
					TIME_OUT_VALUE=2*TIME_OUT_VALUE;	//Doubling the time-out period
					clientSocket.setSoTimeout(TIME_OUT_VALUE);	//Re-setting the time-out for the socket
					System.out.print("\nSending the request to the server...");
					clientSocket.send(sentPacket);	//Re-sending the UDP packet
				}//if
			}//catch			
		}//while
	}//main
	
	public static int getMeasurementID()
	{	//Returns a random measurement ID from the data file
		ArrayList<Integer> measurementID = new ArrayList<Integer>();	//Creating ArrayList to store measurement IDs from data file
		BufferedReader br;	//To read the contents of data file
        try 
        {	//Reading the contents of data file
            br = new BufferedReader(new FileReader("C:\\Users\\DELL\\Google Drive\\eclipse projects\\Project_640\\data.txt"));
            try
            {	//Extracting and storing the measurement IDs from the data file to ArrayList
            	String temp;
            	while((temp = br.readLine()) != null ) 		//Reads the next line
                {
            		Scanner scanner = new Scanner(temp);
            		while (scanner.hasNextInt())	//Checks for next available integer	(as only measurement IDs are integer, temperature are double)
            			measurementID.add(scanner.nextInt());	//Appending the measurement ID to the ArrayList
                }//while
            } //try
            catch (IOException e) 
            {
            	System.out.println(e);
            }//catch
        }//try 
        catch (FileNotFoundException e) 
        {
        	System.out.println(e);
        }//catch
        //Creating a random index number
        //Accessing the measurement ID at this random index in the ArrayList
        //Limiting this measurement ID to 16-bit unsigned integer by logical AND with 0xFFFF
        return measurementID.get((int)(Math.random()*measurementID.size())) & 0xFFFF;	
	}//getMeasurementID
	
	public static String integrityCheck(String checkStr)
	{
		//Declaring an integer array to store paired bytes from byte array
		//Initializing this array to half the size of the byte array
		int[] checkSumArr = new int[checkStr.getBytes().length/2];
		if(checkStr.getBytes().length%2!=0)		//Checking for odd number of elements in the byte array
			checkSumArr = new int[(checkStr.getBytes().length/2)+1];	//Increasing the checksum array size by 1 for odd number of elements 
		int i=0,j=0,S=0,index,C=7919,D=65536;
		
		//Converting the character sequence to a sequence of 16-bit words by pairing consecutive bytes		
		while(i<checkStr.getBytes().length)
		{
			if(checkStr.getBytes().length%2!=0 && i==checkStr.getBytes().length-1)	//Checking for last element in case of odd number elements byte array
				checkSumArr[j++]=(int) (checkStr.getBytes()[i++] << 8 | 0);	//Appending zeros as LSB to last element in odd-length byte array  to form 16-bit word
			else
				checkSumArr[j++]=(int) (checkStr.getBytes()[i++] << 8 | checkStr.getBytes()[i++]);	//Left shifting first byte as MSB and appending the second byte as LSB to form 16-bit word					
		}//while		
		//Performing integrity checksum calculations
		for(int k=0;k<checkSumArr.length;k++)
		{
			index = S^checkSumArr[k];
			S=(C*index)%D;			
		}//for
		return Integer.toString(S&0xFFFF);	//Converting and returning resultant checksum value to 16-bit unsigned by logical AND with 0xFFFF
	}//integrityCheck
}//class
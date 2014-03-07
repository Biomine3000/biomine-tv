package org.bm3k.abboe.common;

import com.google.common.net.MediaType;

public class BusinessMediaType {
    public static final MediaType PLAINTEXT = MediaType.PLAIN_TEXT_UTF_8;
    public static final MediaType PNG = MediaType.PNG;
    public static final MediaType JPEG = MediaType.JPEG;
    public static final MediaType GIF = MediaType.GIF;
    public static final MediaType MP3 = MediaType.create("audio", "mp3");

    /** Peaceful proposal for a zombi session, containing name of proposer and a time interval */
    public static final MediaType ZOMBI_PROPOSAL = MediaType.create("application", "zombi-proposal");

	/** A request to commence some kind chaos in the receivers Biomine TV, in order to boost zombi alertness. */
	public static final MediaType ZOMBI_ALERT = MediaType.create("application", "zombi-alert");

	/**
	 * Announcement of a plätkä match soon to be initiated. Meta-data format to be decided, probably represented as JSON.
	 * Relevant information includes, but is not limited to: match (or tournament?) participants, time, location, mode of play
	 * (matsi 40:een, 5 min, etc)
	 */
	public static final MediaType PLATKA_ANNOUNCEMENT = MediaType.create("application", "platka-announcement");

	/** format to be decided, probably represented as JSON */
	public static final MediaType ZOMBI_PROBABILITY_ANNOUNCEMENT =
            MediaType.create("message", "zombiprobabilityannouncement");

	/** format to be decided, probably represented as JSON */
	public static final MediaType BIOMINE3000_SOFTWARE_AVAILABILITY_ANNOUNCEMENT =
            MediaType.create("application", "biomine3000-software-availability-announcement");

	/** URL to an already existing image in the familiar INTERNET. TODO: surely there can exist URLs in the familiar 
	 * INTERNET that do not point to images. */
    public static final MediaType URL = MediaType.create("text", "url");
    
    public static final MediaType BIOMINE_URL = MediaType.create("text", "biomine-url");

    /** Mielivaltaista kontenttia */
	public static final MediaType ARBITRARY = MediaType.create("application", "arbitrary");

	/**
	 * Announcement of an BIOMINE COMPETITION. This rather complex concept remains yet to be defined exactly,
	 * but all veterans of ttnr competition must know what is meant by this.
	 */
	public static final MediaType COMPETITION = MediaType.create("application", "biomine-competition");

	/** An entry participating in an COMPETITION described above. */
	public static final MediaType COMPETITION_ENTRY = MediaType.create("application", "biomine-competition-entry");

    public static MediaType getByExtension(String extension) {
   	    if (extension.equals("gif")) {
   	        return GIF;
   	    }
   	    else if (extension.equals("jpg")) {
   	        return JPEG;
   	    }
   	    else if (extension.equals("png")) {
   	        return PNG;
   	    }
   	    else {
   	        return null;
   	    }
   	}
}

package com.vauff.discordchatfilter;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

public class Config extends JSONObject
{
	private File file = new File(Util.getJarLocation() + "config.json");

	public Config() throws Exception
	{
		super(getCfgJson());

		if (!file.exists())
		{
			file.createNewFile();
			put("discordToken", "");
			put("mode", 1); // 1 = parse chat history 2 = delete saved messages
			put("messageCount", 10000);
			put("channelIds", new JSONArray());
			save();
		}
	}

	public String getToken()
	{
		return getString("discordToken");
	}

	public int getMode()
	{
		return getInt("mode");
	}

	public int getMessageCount()
	{
		return getInt("messageCount");
	}

	public long[] getChannelIds()
	{
		return getLongArray("channelIds");
	}

	private long[] getLongArray(String name)
	{
		JSONArray json = getJSONArray(name);
		int length = json.length();
		long[] longs = new long[length];

		for (int i = 0; i < length; i++)
			longs[i] = json.getLong(i);

		return longs;
	}

	private void save() throws IOException
	{
		FileUtils.writeStringToFile(file, toString(4), "UTF-8");
	}

	// Required workaround due to "Call to 'super()' must be first statement in constructor body"
	private static String getCfgJson() throws Exception
	{
		File file = new File(Util.getJarLocation() + "config.json");

		if (file.exists())
			return Util.getFileContents(file);
		else
			return "{}";
	}
}

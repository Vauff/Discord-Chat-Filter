package com.vauff.discordchatfilter;

import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.presence.ClientPresence;
import discord4j.rest.http.client.ClientException;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class Main
{
	public static GatewayDiscordClient gateway;
	public static Logger log;
	public static Config cfg;

	public static void main(String[] args)
	{
		try
		{
			boolean exit = false;

			log = LogManager.getLogger(Main.class);
			cfg = new Config();

			if (cfg.getToken().equals(""))
			{
				log.fatal("You need to provide a bot token, please add one obtained from https://discord.com/developers/applications to the discordToken option in config.json");
				exit = true;
			}

			if (cfg.getChannelIds().length == 0)
			{
				log.fatal("You must provide at least one channel ID in config.json");
				exit = true;
			}

			if (exit)
				System.exit(1);

			log.info("Starting Discord Chat Filter in mode " + cfg.getMode() + "...");

			gateway = DiscordClient.create(cfg.getToken()).gateway()
				.withEventDispatcher(eventDispatcher ->
				{
					var event1 = eventDispatcher.on(ReadyEvent.class).doOnNext(Main::onReady);
					return Mono.when(event1);
				})
				.login().block();

			// Keep app alive by waiting for disconnect
			gateway.onDisconnect().block();
		}
		catch (Exception e)
		{
			log.error("", e);
		}
	}

	public static void onReady(ReadyEvent event)
	{
		try
		{
			Thread.sleep(5000);
			gateway.updatePresence(ClientPresence.invisible()).block();

			switch (cfg.getMode())
			{
				case 1 -> parseChat();
				case 2 -> deleteMessages();
				default -> log.error("Invalid mode " + cfg.getMode());
			}

			log.info("Finished processing");
			System.exit(0);
		}
		catch (Exception e)
		{
			log.error("", e);
		}
	}

	public static void parseChat() throws Exception
	{
		List<String> regexFilters = new ArrayList<>();

		try (BufferedReader br = new BufferedReader(new FileReader(Util.getJarLocation() + "regex.txt")))
		{
			String line;

			while ((line = br.readLine()) != null)
				regexFilters.add(line.replace("\n", "").replace("\r", ""));
		}

		for (long channelId : cfg.getChannelIds())
		{
			Snowflake before = Snowflake.of(Instant.now());
			MessageChannel channel = ((MessageChannel) gateway.getChannelById(Snowflake.of(channelId)).block());
			JSONArray jsonArray = new JSONArray();
			int messagesProcessed = 0;

			log.info("Processing channel " + channelId);

			while (true)
			{
				List<Message> messages = channel.getMessagesBefore(before).take(cfg.getMessageCount()).collectList().block();

				for (Message m : messages)
				{
					for (String regex : regexFilters)
					{
						if (Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(m.getContent()).find())
						{
							JSONObject json = new JSONObject();

							json.put("id", m.getId().asLong());
							json.put("match", regex);
							json.put("content", m.getContent());
							jsonArray.put(json);

							break;
						}
					}
				}

				before = messages.get(messages.size() - 1).getId();
				messagesProcessed += cfg.getMessageCount();
				log.info("Processed ~" + messagesProcessed + " messages in channel " + channelId);

				// We reached beginning of the channel
				if (messages.size() != cfg.getMessageCount())
					break;
			}

			if (!jsonArray.isEmpty())
			{
				File file = new File(Util.getJarLocation() + channelId + ".json");
				JSONObject json = new JSONObject().put("messages", jsonArray);

				FileUtils.writeStringToFile(file, json.toString(4), "UTF-8");
			}

			log.info("Finished processing channel " + channelId);
		}
	}

	public static void deleteMessages() throws Exception
	{
		for (long channelId : cfg.getChannelIds())
		{
			File file = new File(Util.getJarLocation() + channelId + ".json");
			JSONObject json = new JSONObject(Util.getFileContents(file));
			int deletedCount = 0;

			log.info("Processing channel " + channelId);

			for (Object object : json.getJSONArray("messages"))
			{
				long id = ((JSONObject) object).getLong("id");

				try
				{
					gateway.getMessageById(Snowflake.of(channelId), Snowflake.of(id)).block().delete().block();
					log.info("Deleted message " + id);
					deletedCount++;
				}
				catch (ClientException e)
				{
					if (e.getStatus().code() == 404)
						log.warn("Message " + id + " was already deleted");
					else
						throw e;
				}
			}

			log.info("Finished processing " + deletedCount + " message deletions in channel " + channelId);
		}
	}
}

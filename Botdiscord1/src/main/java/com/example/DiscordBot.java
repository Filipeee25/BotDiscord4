package com.example;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceJoinEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import okhttp3.*;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DiscordBot extends ListenerAdapter {

    private static final String DISCORD_BOT_TOKEN = "MTI0OTE1OTI1NjQ4NjMxODE1MA.Gtes37.ycIYcqFCZySVcrj9w_U5yT4cw_7L1cguQz1jE0"; // Substitua pelo seu token
    private static final String SHEETDB_API_URL = "https://sheetdb.io/api/v1/a9yxc50o8g9gs"; // Substitua pela sua URL do SheetDB
    private Map<VoiceChannel, List<Member>> voiceChannelMembers = new HashMap<>();

    public static void main(String[] args) throws LoginException {
        JDABuilder builder = JDABuilder.createDefault(DISCORD_BOT_TOKEN);
        builder.enableIntents(GatewayIntent.GUILD_VOICE_STATES, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT);
        builder.addEventListeners(new DiscordBot());
        builder.build();
        System.out.println("Bot está online!");
    }

    @Override
    public void onGuildVoiceJoin(GuildVoiceJoinEvent event) {
        VoiceChannel channelJoined = (VoiceChannel) event.getChannelJoined();
        List<Member> membersInChannel = channelJoined.getMembers();
        voiceChannelMembers.put(channelJoined, membersInChannel);
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        String message = event.getMessage().getContentRaw();
        String[] args = message.split("\\s+");
        if (args[0].equalsIgnoreCase("!listmembers")) {
            if (args.length < 2) {
                event.getChannel().sendMessage("Uso correto: !listmembers <nome_do_canal>").queue();
                return;
            }

            String channelName = args[1];
            VoiceChannel channel = event.getGuild().getVoiceChannelsByName(channelName, true).stream().findFirst().orElse(null);
            if (channel == null) {
                event.getChannel().sendMessage("Canal não encontrado!").queue();
                return;
            }

            List<Member> members = voiceChannelMembers.get(channel);
            if (members == null || members.isEmpty()) {
                event.getChannel().sendMessage("Nenhum membro encontrado no canal " + channel.getName()).queue();
                return;
            }

            StringBuilder memberList = new StringBuilder();
            memberList.append("Membros no canal ").append(channel.getName()).append(":\n");
            for (Member member : members) {
                memberList.append(member.getEffectiveName()).append("\n");
            }

            event.getChannel().sendMessage(memberList.toString()).queue();

            // Envia os nomes para o SheetDB
            try {
                SheetDBUtil.sendData(SHEETDB_API_URL, channel.getName(), members);
                event.getChannel().sendMessage("Dados enviados para a planilha com sucesso!").queue();
            } catch (IOException e) {
                event.getChannel().sendMessage("Ocorreu um erro ao enviar os dados para a planilha.").queue();
                e.printStackTrace();
            }
        }
    }
}

// Utilitário para enviar dados para o SheetDB
class SheetDBUtil {
    public static void sendData(String apiUrl, String channelName, List<Member> members) throws IOException {
        OkHttpClient client = new OkHttpClient();
        StringBuilder jsonData = new StringBuilder();
        jsonData.append("{\"data\":[");
        for (Member member : members) {
            jsonData.append("{\"Member\":\"").append(member.getEffectiveName()).append("\"},");
        }
        jsonData.deleteCharAt(jsonData.length() - 1); // Remove a última vírgula
        jsonData.append("]}");

        RequestBody body = RequestBody.create(jsonData.toString(), MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(apiUrl)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
        }
    }
}

package opViewLineBot;

import com.google.common.io.ByteStreams;
import com.linecorp.bot.client.LineMessagingService;
import com.linecorp.bot.model.PushMessage;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.action.MessageAction;
import com.linecorp.bot.model.action.PostbackAction;
import com.linecorp.bot.model.action.URIAction;
import com.linecorp.bot.model.event.*;
import com.linecorp.bot.model.event.message.*;
import com.linecorp.bot.model.event.source.GroupSource;
import com.linecorp.bot.model.event.source.RoomSource;
import com.linecorp.bot.model.event.source.Source;
import com.linecorp.bot.model.message.*;
import com.linecorp.bot.model.message.imagemap.ImagemapArea;
import com.linecorp.bot.model.message.imagemap.ImagemapBaseSize;
import com.linecorp.bot.model.message.imagemap.MessageImagemapAction;
import com.linecorp.bot.model.message.imagemap.URIImagemapAction;
import com.linecorp.bot.model.message.template.ButtonsTemplate;
import com.linecorp.bot.model.message.template.CarouselColumn;
import com.linecorp.bot.model.message.template.CarouselTemplate;
import com.linecorp.bot.model.message.template.ConfirmTemplate;
import com.linecorp.bot.model.profile.UserProfileResponse;
import com.linecorp.bot.model.response.BotApiResponse;
import lombok.NonNull;
import lombok.Value;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.linecorp.bot.spring.boot.annotation.EventMapping;
import com.linecorp.bot.spring.boot.annotation.LineMessageHandler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import retrofit2.Response;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Package : opViewLineBot
 * --
 * Description :
 * Author : jasonlin
 * Date : 2016/12/30
 */
@LineMessageHandler
@Slf4j
@RestController
public class LineBotController {
    @Autowired
    private LineMessagingService lineMessagingService;

    private static String createUri(String path) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                                          .path(path).build()
                                          .toUriString();

    }

    private static DownloadedContent saveContent(String ext, ResponseBody responseBody) {
        log.info("Got content-type: {}", responseBody.contentType());
        DownloadedContent tempFile = createTempFile(ext);
        try (OutputStream outputStream = Files.newOutputStream(tempFile.path)) {
            ByteStreams.copy(responseBody.byteStream(), outputStream);
            log.info("Saved {}: {}", ext, tempFile);
            return tempFile;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static DownloadedContent createTempFile(String ext) {
        long unixTime = System.currentTimeMillis() / 1000L;
        String fileName = String.valueOf(unixTime) + "-" + UUID.randomUUID().toString() + '.' + ext;
        Path tempFile = LineBotApplication.downloadedContentDir.resolve(fileName);
        tempFile.toFile().deleteOnExit();
        return new DownloadedContent(tempFile, createUri("/downloaded/"+tempFile.getFileName()));
    }

    @RequestMapping("/")
    public String index() {
        Greeter greeter = new Greeter();
        return greeter.sayHello();
    }

    @RequestMapping("/greeting")
    public String greeting(@RequestParam(value="targetId") String targetId, @RequestParam(value="message", defaultValue = "Hello!") String message) {
        if (ObjectUtils.isEmpty(targetId)) {
            return "Error in sending messages : targetId isn't given.";
        }

        TextMessage textMessage = new TextMessage(message);
        PushMessage pushMessage = new PushMessage(
                targetId,
                textMessage
        );

        Response<BotApiResponse> apiResponse =
                null;
        try {
            apiResponse = lineMessagingService
                    .pushMessage(pushMessage)
                    .execute();
            return String.format("Sent messages: %s %s", apiResponse.message(), apiResponse.code());
        } catch (IOException e) {
            e.printStackTrace();
            return String.format("Error in sending messages : %s", e.toString());
        }
    }

    @EventMapping
    public void handleDefaultMessageEvent(Event event) {
        log.info("Received message(Ignored): {}", event);
    }

    @EventMapping
    public void handleTextMessageEvent(MessageEvent<TextMessageContent> event) throws IOException{
        log.info("Got text event: {}", event);
        handleTextContent(event.getReplyToken(), event, event.getMessage());
    }

    @EventMapping
    public void handleUnfollowEvent(UnfollowEvent event) {
        log.info("Got unfollow event: {}", event);
    }

    @EventMapping
    public void handleFollowEvent(FollowEvent event) {
        log.info("Got follow event: {}", event);
        String replyToken = event.getReplyToken();
        try {
            this.replyText(replyToken, "Hello ! " + getUserName(event.getSource().getUserId()));
        } catch (IOException e) {
            e.printStackTrace();
            this.replyText(replyToken, "Hello !");
        }
    }

    @EventMapping
    public void handleJoinEvent(JoinEvent event) {
        log.info("Got join event: {}", event);
        String replyToken = event.getReplyToken();
        Source source = event.getSource();
        if (source instanceof GroupSource) {
            this.replyText(replyToken, "I joined a group : " + ((GroupSource) source).getGroupId());
        } else if (source instanceof RoomSource) {
            this.replyText(replyToken, "I joined a room : " + ((RoomSource) source).getRoomId());
        } else {
            this.replyText(replyToken, "I joined ??? : " + source);
        }
    }

    @EventMapping
    public void handlePostbackEvent(PostbackEvent event) throws IOException {
        log.info("Got postBack event: {}", event);
        String replyToken = event.getReplyToken();
        String data = event.getPostbackContent().getData();
        Source source = event.getSource();
        switch (data) {
            case "bye:yes": {
                this.replyText(replyToken, "Bye, see you again ...");
                lineMessagingService.leaveGroup(
                        ((GroupSource) source).getGroupId()
                ).execute();
                break;
            }

            case "bye:no": {
                this.replyText(replyToken, "Ok, let us keep talking!");
                break;
            }

            default:
                this.replyText(replyToken, "Got postback event : " + event.getPostbackContent().getData());
        }
    }

    @EventMapping
    public void handleStickerMessageEvent(MessageEvent<StickerMessageContent>  event) {
        log.info("Got sticker event: {}", event);
        handleSticker(event.getReplyToken(), event.getMessage());
    }

    @EventMapping
    public void handleLocationMessageEvent(MessageEvent<LocationMessageContent> event) {
        log.info("Got location event: {}", event);
        LocationMessageContent locationMessage = event.getMessage();
        reply(event.getReplyToken(), new LocationMessage(
                locationMessage.getTitle(),
                locationMessage.getAddress(),
                locationMessage.getLatitude(),
                locationMessage.getLongitude()
        ));
    }

    @EventMapping
    public void handleImageMessageEvent(MessageEvent<ImageMessageContent> event) {
        log.info("Got image event: {}", event);
        replyText(event.getReplyToken(), event.getMessage().toString());
    }

    @EventMapping
    public void handleAudioMessageEvent(MessageEvent<AudioMessageContent> event) {
        log.info("Got audio event: {}", event);
        replyText(event.getReplyToken(), event.getMessage().toString());
    }

    @EventMapping
    public void handleVideoMessageEvent(MessageEvent<VideoMessageContent> event) {
        log.info("Got video event: {}", event);
        replyText(event.getReplyToken(), event.getMessage().toString());
    }

    private void replyText(@NonNull String replyToken, @NonNull String message) {
        if (replyToken.isEmpty()) {
            throw new IllegalArgumentException("replyToken must not be empty");
        }
        if (message.length() > 1000) {
            message = message.substring(0, 1000 - 2) + "……";
        }
        this.reply(replyToken, new TextMessage(message));
    }

    private void reply(@NonNull String replyToken, @NonNull Message message) {
        reply(replyToken, Collections.singletonList(message));
    }

    private void reply(@NonNull String replyToken, @NonNull List<Message> messages) {
        try {
            Response<BotApiResponse> apiResponse = lineMessagingService
                    .replyMessage(new ReplyMessage(replyToken, messages))
                    .execute();
            log.info("Sent messages: {} {}", apiResponse.message(), apiResponse.code());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String getUserName(@NonNull String userId) throws IOException {
        Response<UserProfileResponse> response = lineMessagingService
                .getProfile(userId)
                .execute();
        if (response.isSuccessful()) {
            UserProfileResponse profiles = response.body();
            return profiles.getDisplayName();
        } else {
            return response.errorBody().string();
        }
    }

    private UserProfileResponse getUserProfile(@NonNull String userId) throws IOException {
        Response<UserProfileResponse> response = lineMessagingService
                .getProfile(userId)
                .execute();
        return response.body();
    }

    private void handleTextContent(String replyToken, Event event, TextMessageContent content) throws IOException {
        String text = content.getText();
        String helpText = "You can input the following messages to see each results:\n(0)help,\n(1)profile,\n(2)bye,\n(3)confirm,\n(4)buttons,\n(5)carousel,\n(6)imagemap.";

        log.info("Got text message from {}: {}", replyToken, text);
        switch (text) {
            case "readme": case "0": {
                this.replyText(replyToken, helpText);
                break;
            }
            case "profile": case "1": {
                String userId = event.getSource().getUserId();
                if (userId != null) {
                    UserProfileResponse userProfile = getUserProfile(userId);
                    String pictureUrlURL = userProfile.getPictureUrl() == null ? "(Not Set)" : userProfile.getPictureUrl();
                    String displayName = userProfile.getDisplayName() == null ? "(Not Set)" : userProfile.getDisplayName();
                    String displayStatus = userProfile.getStatusMessage() == null ? "(Not Set)" : userProfile.getStatusMessage();
                    OkHttpClient client = new OkHttpClient();
                    Request request = new Request.Builder().url(pictureUrlURL).build();
                    okhttp3.Response response = client.newCall(request).execute();
                    DownloadedContent jpg = saveContent("jpg", response.body());
                    this.reply(replyToken, Arrays.asList(
                            new ImageMessage(jpg.getUri(), jpg.getUri()),
                            new TextMessage("Hi, " + displayName),
                            new TextMessage("Your status is : " + displayStatus)
                    ));
                } else {
                    this.replyText(replyToken, "Bot can only get a user's profile when 1:1 chat");
                }
                break;
            }
            case "bye": case "2": {
                Source source = event.getSource();
                if (source instanceof GroupSource) {
                    ConfirmTemplate confirmTemplate = new ConfirmTemplate(
                            "I am going to leave this group, are you sure?",
                            new PostbackAction("Yes,right now!", "bye:yes"),
                            new PostbackAction("No,stay here!", "bye:no")
                    );
                    TemplateMessage templateMessage = new TemplateMessage(
                            "Sorry, I don't support the Confirm function in your platform. :(",
                            confirmTemplate
                    );
                    this.reply(replyToken, templateMessage);
                } else if (source instanceof RoomSource) {
                    ConfirmTemplate confirmTemplate = new ConfirmTemplate(
                            "I am going to leave this room, are you sure?",
                            new PostbackAction("Yes,right now!", "bye:yes"),
                            new PostbackAction("No,stay here!", "bye:no")
                    );
                    TemplateMessage templateMessage = new TemplateMessage(
                            "Sorry, I don't support the Confirm function in your platform. :(",
                            confirmTemplate
                    );
                    this.reply(replyToken, templateMessage);
                } else {
                    this.replyText(replyToken, "Bot can't leave from 1:1 chat");
                }
                break;
            }
            case "confirm": case "3": {
                ConfirmTemplate confirmTemplate = new ConfirmTemplate(
                        "Do you love Taiwan?",
                        new MessageAction("Yes", "Yes, I love Taiwan!"),
                        new MessageAction("No", "No, it is just an oni island!")
                );
                TemplateMessage templateMessage = new TemplateMessage(
                        "Sorry, I don't support the Confirm function in your platform. :(",
                        confirmTemplate
                );
                this.reply(replyToken, templateMessage);
                break;
            }
            case "buttons": case "4": {
                String imageUrl = createUri("/static/buttons/goodsmile.jpg");
                ButtonsTemplate buttonsTemplate = new ButtonsTemplate(
                        imageUrl,
                        "My button sample",
                        "Hello, my button",
                        Arrays.asList(
                                new URIAction("Go to line.me",
                                              "https://line.me"),
                                new PostbackAction("Say opViewLineBot 1",
                                                   "Hello 你好!"),
                                new PostbackAction("Say opViewLineBot 2",
                                                   "Hello 你好!!",
                                                   "Hello 你好!!"),
                                new MessageAction("Say message",
                                                  "Rice=米")
                        )
                );
                TemplateMessage templateMessage = new TemplateMessage("Sorry, I don't support the Button function in your platform. :(", buttonsTemplate);
                this.reply(replyToken, templateMessage);
                break;
            }
            case "carousel": case "5" : {
                String imageUrl1 = createUri("/static/buttons/figure1.jpg");
                String imageUrl2 = createUri("/static/buttons/figure2.jpg");
                String imageUrl3 = createUri("/static/buttons/figure3.jpg");
                CarouselTemplate carouselTemplate = new CarouselTemplate(
                        Arrays.asList(
                                new CarouselColumn(
                                        imageUrl1,
                                        "黑岩射手",
                                        "NT$ 1,357 (¥ 4,960)",
                                        Arrays.asList(
                                                new URIAction(
                                                        "Buy it !",
                                                        "http://global.rakuten.com/zh-tw/store/amiami/item/figure-026643/"
                                                )
                                        )
                                ),
                                new CarouselColumn(
                                        imageUrl2,
                                        "加藤恵",
                                        "NT$ 1,017 (¥ 3,720)",
                                        Arrays.asList(
                                                new URIAction(
                                                        "Buy it !",
                                                        "http://global.rakuten.com/zh-tw/store/amiami/item/figure-026535/"
                                                )
                                        )
                                ),
                                new CarouselColumn(
                                        imageUrl3,
                                        "-艦これ- Iowa",
                                        "NT$ 1,748 (¥ 6,390)",
                                        Arrays.asList(
                                                new URIAction(
                                                        "Buy it !",
                                                        "http://global.rakuten.com/zh-tw/store/amiami/item/figure-024873/"
                                                )
                                        )
                                )
                        )
                );
                TemplateMessage templateMessage = new TemplateMessage("Sorry, I don't support the Carousel function in your platform. :(", carouselTemplate);
                this.reply(replyToken, templateMessage);
                break;
            }
            case "imagemap": case "6": {
                this.reply(replyToken, Arrays.asList(
                        new TextMessage("Please press any text in the picture to see what will happen."),
                        new ImagemapMessage(
                                createUri("/static/rich"),
                                "Sorry, I don't support the Imagemap function in your platform. :(",
                                new ImagemapBaseSize(1040, 1040),
                                Arrays.asList(
                                        new URIImagemapAction(
                                                "https://store.line.me/family/manga/en",
                                                new ImagemapArea(0, 0, 520, 520)
                                        ),
                                        new URIImagemapAction(
                                                "https://store.line.me/family/music/en",
                                                new ImagemapArea(520, 0, 520, 520)
                                        ),
                                        new URIImagemapAction(
                                                "https://store.line.me/family/play/en",
                                                new ImagemapArea(0, 520, 520, 520)
                                        ),
                                        new MessageImagemapAction(
                                                "URANAI!",
                                                new ImagemapArea(520, 520, 520, 520)
                                        )
                                )
                        )
                ));
                break;
            }
            default:
                log.info("Returns echo message {}: {}", replyToken, text);
                this.reply(
                        replyToken,
                        Arrays.asList(
                                new TextMessage("You said : " + text),
                                new TextMessage(helpText)
                        )
                );
        }
    }

    private void handleSticker(String replyToken, StickerMessageContent content) {
        reply(replyToken, new StickerMessage(
                content.getPackageId(),
                content.getStickerId()
        ));
    }

    @Value
    public static class DownloadedContent {
        Path path;
        String uri;
    }
}

package org.marble.commons.executor.streamer;

import org.marble.model.domain.model.Job;
import org.marble.model.domain.model.Post;
import org.marble.model.domain.model.Topic;
import org.marble.model.model.GeoLocation;
import org.marble.model.model.JobParameters;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.marble.commons.exception.InvalidExecutionException;
import org.marble.commons.exception.InvalidModuleException;
import org.marble.commons.exception.InvalidTopicException;
import org.marble.commons.executor.extractor.TwitterExtractionExecutor;
import org.marble.commons.executor.processor.ProcessorExecutor;
import org.marble.commons.model.JobRestResult;
import org.marble.commons.model.RestResult;
import org.marble.commons.service.JobService;
import org.marble.commons.service.PostService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import twitter4j.StallWarning;
import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.StatusListener;

public class TwitterStreamingListener implements StatusListener {

  JobService jobService;

  PostService postService;
  private boolean failure = false;
  private boolean stopping = false;
  private String topicName;
  private String[] keywords;
  private ArrayList<double[]> locations;
  private Topic topic;
  private static final Logger log = LoggerFactory.getLogger(TwitterExtractionExecutor.class);
  private long count;
  private Job job;

  public TwitterStreamingListener(Topic topic, Job job, PostService postService,
      JobService executionService) {
    this.topic = topic;
    String keywordsString = topic.getKeywords();
    if (keywordsString != null) {
      keywordsString = keywordsString.toLowerCase();
    }
    this.keywords = keywordsString.split("\\|");

    this.job = job;
    this.postService = postService;
    this.topicName = topic.getName();
    this.jobService = executionService;

    Double longitude = topic.getGeoLongitude();
    Double latitude = topic.getGeoLatitude();
    Double radius = topic.getGeoRadius();
    if (longitude != null && latitude != null && radius != null) {
      this.locations = getSquareAroundPoint(latitude, longitude, radius);
    }
    count = 0;
  }

  private ArrayList<double[]> getSquareAroundPoint(Double lat, Double lon, Double radius) {
    double R = 6371;
    double distance = radius.doubleValue();

    double north = (lat * Math.PI / 180 + distance / R) * 180 / Math.PI;
    if (north > 90)
      north = 90;

    double neast = (lon + (Math.atan2(Math.sin(distance / R) * Math.cos(lat * Math.PI / 180),
        Math.cos(distance / R)
            - Math.sin(lat * Math.PI / 180) * Math.sin(lat * Math.PI / 180 + distance / R)))
        * 180 / Math.PI);
    double nwest = (lon + (Math.atan2(-Math.sin(distance / R) * Math.cos(lat * Math.PI / 180),
        Math.cos(distance / R)
            - Math.sin(lat * Math.PI / 180) * Math.sin(lat * Math.PI / 180 + distance / R)))
        * 180 / Math.PI);

    double south = (lat * Math.PI / 180 - distance / R) * 180 / Math.PI;
    if (south < -90)
      south = -90;
    double seast = (lon + (Math.atan2(Math.sin(distance / R) * Math.cos(lat * Math.PI / 180),
        Math.cos(distance / R)
            - Math.sin(lat * Math.PI / 180) * Math.sin(lat * Math.PI / 180 - distance / R)))
        * 180 / Math.PI);
    double swest = (lon + (Math.atan2(-Math.sin(distance / R) * Math.cos(lat * Math.PI / 180),
        Math.cos(distance / R)
            - Math.sin(lat * Math.PI / 180) * Math.sin(lat * Math.PI / 180 - distance / R)))
        * 180 / Math.PI);

    double east = Math.max(seast, neast);
    double west = Math.min(swest, nwest);
    while (east > 180)
      east -= 360;
    while (east <= -180)
      east += 360;
    while (west > 180)
      east -= 360;
    while (west <= -180)
      east += 360;
    ArrayList<double[]> coords = new ArrayList<double[]>();
    double[] southwest = {west, south};
    double[] northeast = {east, north};
    coords.add(southwest);
    coords.add(northeast);
    return coords;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((keywords == null) ? 0 : keywords.hashCode());
    result = prime * result + ((topicName == null) ? 0 : topicName.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    TwitterStreamingListener other = (TwitterStreamingListener) obj;
    if (keywords == null) {
      if (other.keywords != null)
        return false;
    } else if (!keywords.equals(other.keywords))
      return false;
    if (topicName == null) {
      if (other.topicName != null)
        return false;
    } else if (!topicName.equals(other.topicName))
      return false;
    return true;
  }

  public String getTopicName() {
    return topicName;
  }

  public void setTopicName(String topicName) {
    this.topicName = topicName;
  }

  public long getCount() {
    return count;
  }

  public void setCount(long count) {
    this.count = count;
  }

  public void setKeywords(String[] keywords) {
    this.keywords = keywords;
  }

  public String[] getKeywords() {
    return keywords;
  }

  public Topic getTopic() {
    return topic;
  }

  public void setTopic(Topic topic) {
    this.topic = topic;
  }

  public String getLanguage() {
    return topic.getLanguage();
  }

  public ArrayList<double[]> getLocations() {
    return locations;
  }

  public Job getJob() {
    return job;
  }

  public void setJob(Job job) {
    this.job = job;
  }

  public void onStatus(Status status) {

    String msg =
        "Received new post <" + status.getId() + ">. Matching for topic <" + topic.getName() + ">.";
    log.info(msg);
    job.appendLog(msg);

    try {
      jobService.save(job);
    } catch (InvalidExecutionException e) {
      log.error("Couldn't persist job object.", e);
    }

    if (keywords != null && keywords.length > 0) {
      String[] phrases = keywords;
      String tweetText = status.getText().toLowerCase();
      Boolean matchesOne = matchTextWithKeywords(phrases, tweetText);

      if (!matchesOne) {
        log.debug("Tweet didn't match keywords <" + Arrays.toString(keywords) + ">");
        return;
      }
    }

    if (topic.getLanguage() != null && !"".equals(topic.getLanguage())) {
      if (!topic.getLanguage().equals(status.getLang())) {
        log.debug("Tweet didn't match language <" + topic.getLanguage() + ">");
        return;
      }
    }

    Double longitude = topic.getGeoLongitude();
    Double latitude = topic.getGeoLatitude();
    Double radius = topic.getGeoRadius();

    if (longitude != null && latitude != null && radius != null) {
      if (status.getGeoLocation() == null) {
        log.debug(
            "Tweet don't have geolocation info. Discarding for topic <" + topic.getName() + ">");
        return;
      } else {
        GeoLocation tweetGeo = new GeoLocation(status.getGeoLocation());
        double R = 6371; // Earth's radius
        double tweetLat = tweetGeo.getLatitude() * Math.PI / 180;
        double tweetLng = tweetGeo.getLongitude() * Math.PI / 180;
        double centerLat = latitude.doubleValue() * Math.PI / 180;
        double centerLng = longitude.doubleValue() * Math.PI / 180;
        double dist = Math.acos(Math.sin(tweetLat) * Math.sin(centerLat)
            + Math.cos(tweetLat) * Math.cos(centerLat) * Math.cos(tweetLng - centerLng)) * R;
        if (dist > radius) {
          log.debug("Tweet is not within geolocation area of topic <" + topic.getName() + ">");
          return;
        }

      }
    }

    // Save the post
    if (!stopping) {

      msg = "Post <" + status.getId() + "> matched topic <" + topic.getName() + ">.";
      log.debug(msg);
      job.appendLog(msg);

      try {
        jobService.save(job);
      } catch (InvalidExecutionException e) {
        log.error("Couldn't persist job object.", e);
      }

      Post streamingStatus = new Post(status, topic.getName());
      try {
        if (postService == null)
          log.error("Data store is null");
        postService.save(streamingStatus);
        failure = false;
      } catch (Exception e) {
        log.error(e.getMessage());
      }

      count++;

      // Calling processor if applicable
      if (topic.getStreamerProcessParameters() != null
          && topic.getStreamerProcessParameters().size() > 0) {
        try {
          LinkedHashSet<JobParameters> extraParameters = new LinkedHashSet<>();
          JobParameters filterParameters = new JobParameters();
          filterParameters.setName(ProcessorExecutor.MARBLE_FILTER);
          Map<String, Object> filterOptions = new HashMap<>();
          filterOptions.put(ProcessorExecutor.MARBLE_FILTER_FROM_ID,
              Long.toString(streamingStatus.getOriginalId()));
          filterOptions.put(ProcessorExecutor.MARBLE_FILTER_TO_ID,
              Long.toString(streamingStatus.getOriginalId()));
          filterParameters.setOptions(filterOptions);
          extraParameters.add(filterParameters);
          jobService.executeProcessor(topicName, job, extraParameters);
        } catch (InvalidTopicException | InvalidExecutionException | InvalidModuleException e) {
          msg = "An error occurred while starting the processor for post <"
              + streamingStatus.getId() + ">.";
          log.error(msg);
          job.appendLog(msg);
        }
      }

    }

    long maxStatuses = 200;
    if (topic.getPostsPerFullExtraction() != null) {
      maxStatuses = topic.getPostsPerFullExtraction();
      if (count > maxStatuses && maxStatuses > 0) {
        if (stopping)
          return;
        stopping = true;
        try {
          msg = "Stopping listener as it has reached the maximum count <" + maxStatuses
              + "> for topic <" + topic.getName() + ">.";
          log.debug(msg);
          job.appendLog(msg);

          try {
            jobService.save(job);
          } catch (InvalidExecutionException e) {
            log.error("Couldn't persist job object.", e);
          }
          jobService.stopStreamer(topic.getName());

        } catch (InvalidTopicException e) {
          log.error("InvalidStreaming", e);
        } catch (InvalidExecutionException e) {
          log.error("InvalidStreaming", e);
        }

        return;
      } else {
        stopping = false;
      }
    }

  }

  private Boolean matchTextWithKeywords(String[] phrases, String tweetText) {
    Boolean matchesOne = Boolean.FALSE;
    for (String phrase : phrases) {
      String[] individualKeywords = phrase.split(" ");
      Integer matches = 0;
      for (String individualKeyword : individualKeywords) {
        if (tweetText.toLowerCase().contains(individualKeyword.toLowerCase())) {
          matches++;
        }
      }
      if (matches == individualKeywords.length) {
        matchesOne = Boolean.TRUE;
        break;
      }
    }
    return matchesOne;
  }

  public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) {

  }

  public void onTrackLimitationNotice(int numberOfLimitedStatuses) {

  }

  public void onScrubGeo(long userId, long upToStatusId) {

  }

  public void onStallWarning(StallWarning warning) {

  }

  public void onException(Exception ex) {
    if (!failure) {
      failure = true;
      // TODO Check what to do here. They sent an email
    }
    failure = true;
    // executionService.useNextAPIKey();
  }

}
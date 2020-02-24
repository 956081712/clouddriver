/*
 * Copyright 2019 Alibaba Group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.alicloud.provider.agent;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.IMAGES;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.NAMED_IMAGES;

import com.aliyuncs.IAcsClient;
import com.aliyuncs.ecs.model.v20140526.DescribeImagesRequest;
import com.aliyuncs.ecs.model.v20140526.DescribeImagesResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeImagesResponse.Image;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.*;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.alicloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.alicloud.provider.AliProvider;
import com.netflix.spinnaker.clouddriver.alicloud.security.AliCloudCredentials;
import java.util.*;
import org.springframework.util.CollectionUtils;

public class AliCloudImageCachingAgent implements CachingAgent, AccountAware {

  AliCloudCredentials account;
  String region;
  ObjectMapper objectMapper;
  IAcsClient client;

  public AliCloudImageCachingAgent(
      AliCloudCredentials account, String region, ObjectMapper objectMapper, IAcsClient client) {
    this.account = account;
    this.region = region;
    this.objectMapper = objectMapper;
    this.client = client;
  }

  static final Collection<AgentDataType> types =
      Collections.unmodifiableCollection(
          new ArrayList<AgentDataType>() {
            {
              add(AUTHORITATIVE.forType(IMAGES.ns));
              add(AUTHORITATIVE.forType(NAMED_IMAGES.ns));
            }
          });

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    Map<String, Collection<CacheData>> resultMap = new HashMap<>(16);
    List<CacheData> imageDatas = new ArrayList<>();
    List<CacheData> nameImageDatas = new ArrayList<>();

    DescribeImagesRequest imagesRequest = new DescribeImagesRequest();
    // imagesRequest.setImageOwnerAlias("system");

    int imagePageNumber = 1;
    int imagePageSize = 50;

    DescribeImagesResponse describeImagesResponse;
    try {
      while (true) {
        imagesRequest.setPageNumber(imagePageNumber);
        imagesRequest.setPageSize(imagePageSize);
        describeImagesResponse = client.getAcsResponse(imagesRequest);
        if (!CollectionUtils.isEmpty(describeImagesResponse.getImages())) {
          imagePageNumber = imagePageNumber + 1;
          for (Image image : describeImagesResponse.getImages()) {
            Map<String, Object> attributes = objectMapper.convertValue(image, Map.class);
            CacheData data =
                new DefaultCacheData(
                    Keys.getImageKey(image.getImageId(), account.getName(), region),
                    attributes,
                    new HashMap<>(16));
            imageDatas.add(data);
          }
          if (describeImagesResponse.getImages().size() < imagePageSize) {
            break;
          }
        } else {
          break;
        }
      }
    } catch (ServerException e) {
      e.printStackTrace();
    } catch (ClientException e) {
      e.printStackTrace();
    }

    int nameImagesPageNumber = 1;
    int nameImagesPageSize = 50;

    DescribeImagesRequest nameImagesRequest = new DescribeImagesRequest();
    // nameImagesRequest.setImageOwnerAlias("self");
    DescribeImagesResponse nameImagesResponse;

    try {
      while (true) {
        nameImagesRequest.setPageNumber(nameImagesPageNumber);
        nameImagesRequest.setPageSize(nameImagesPageSize);
        nameImagesResponse = client.getAcsResponse(nameImagesRequest);
        if (!CollectionUtils.isEmpty(nameImagesResponse.getImages())) {
          nameImagesPageNumber = nameImagesPageNumber + 1;
          for (Image image : nameImagesResponse.getImages()) {
            Map<String, Object> attributes = objectMapper.convertValue(image, Map.class);
            CacheData data =
                new DefaultCacheData(
                    Keys.getNamedImageKey(account.getName(), image.getImageName()),
                    attributes,
                    new HashMap<>(16));
            nameImageDatas.add(data);
          }
          if (nameImagesResponse.getImages().size() < imagePageSize) {
            break;
          }
        } else {
          break;
        }
      }
    } catch (ServerException e) {
      e.printStackTrace();
    } catch (ClientException e) {
      e.printStackTrace();
    }

    resultMap.put(IMAGES.ns, imageDatas);
    resultMap.put(NAMED_IMAGES.ns, nameImageDatas);

    return new DefaultCacheResult(resultMap);
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }

  @Override
  public String getAgentType() {
    return account.getName() + "/" + region + "/" + this.getClass().getSimpleName();
  }

  @Override
  public String getProviderName() {
    return AliProvider.PROVIDER_NAME;
  }

  @Override
  public String getAccountName() {
    return account.getName();
  }
}

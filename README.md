unique-terms-plugin
===========

Elasticsearch Unique Terms Plugin.

This adds the ability to get count of unique terms present in specific field in elasticsearch index for specific time period.
Currently works with hourly based indices with the name format ${index_name_prefix}-yyyy.MM.dd-HH and time range filter for field @timestamp.


License
-------

This is released under Apache License v2


General information
-------------------


Building
--------

Maven is required.

You can build it with the following command:
    $ mvn clean install

This will build the following artifacts:
* elasticsearch-unique-terms-plugin/target/unique-terms-1.0-plugin.zip
  This can be directly installed as elasticsearch plugin.


Installation
------------

Elasticsearch is required.

1)  Copy built artifact to elastcisearch node on which you are going to install the plugin.

2)  $ $ES_HOME/bin/plugin --url file:${path/to/plugin/archive} --install unique-terms

3)  Specify ehcache disk store directory (SSD preferred) by adding system property:

    -Dehcache.disk.store.dir=/path/to/cache

    Normally this can be done by modifying ES_JAVA_OPTS variable.

4)  Make sure that ehcache disk store directory has appropriate read-write permissions.

5)  Make sure that ehcache has appropriate memory configuration (maxBytesLocalHeap, maxBytesLocalDisk attributes in $ES_HOME/plugins/unique-terms/ehcache.xml)

6)  restart elasticsearch node:
    $ service elasticsearch restart

Query example
-------------

General request format:

$  curl -XPUT http://localhost:9200/${index_name_prefix-yyyy.MM.dd-HH}/_unique?clearCache=false -d '{
  "facets": {
    "terms": {
      "terms": {
        "field": "${field_to_get_unique_terms_count}",
        "size": ${number_that_is_much_bigger_than_expected_unique_terms_count},
        "order": "count",
        "exclude": []
      },
      "facet_filter": {
        "fquery": {
          "query": {
            "filtered": {
              "query": {
                "bool": {
                  "should": [
                    {
                      "query_string": {
                        "query": "${some_query_string}"
                      }
                    }
                  ]
                }
              },
              "filter": {
                "bool": {
                  "must": [
                    {
                      "range": {
                        "@timestamp": {
                          "from": 1394841600000,
                          "to": "now"
                        }
                      }
                    },
                    ...
                  ]
                }
              }
            }
          }
        }
      }
    }
  },
  "size": 0
}'

1)  Creating two indices with documents:

$  curl -XPUT 'http://localhost:9200/twitter-2014.03.15-00/'

$  curl -XPUT 'http://localhost:9200/twitter-2014.03.15-00/tweet/1' -d '{
    "tweet" : {
        "@uid" : "UID_1",
        "@timestamp" : "2014-03-15T00:12:12",
        "@message" : "trying out unique terms count plugin"
    }
}'

$  curl -XPUT 'http://localhost:9200/twitter-2014.03.15-01/'

$  curl -XPUT 'http://localhost:9200/twitter-2014.03.15-01/tweet/2' -d '{
    "tweet" : {
        "@uid" : "UID_2",
        "@timestamp" : "2014-03-15T01:12:12",
        "@message" : "trying out unique terms count plugin"
    }
}'

2)  Getting unique terms count for @uid field:

$  curl -XPOST http://localhost:9200/twitter-2014.03.15-00,twitter-2014.03.15-01/_unique -d '{
  "facets": {
    "terms": {
      "terms": {
        "field": "@uid",
        "size": 10,
        "order": "count",
        "exclude": []
      },
      "facet_filter": {
        "fquery": {
          "query": {
            "filtered": {
              "query": {
                "bool": {
                  "should": [
                    {
                      "query_string": {
                        "query": "*"
                      }
                    }
                  ]
                }
              },
              "filter": {
                "bool": {
                  "must": [
                    {
                      "range": {
                        "@timestamp": {
                          "from": 1394841600000,
                          "to": "now"
                        }
                      }
                    }
                  ]
                }
              }
            }
          }
        }
      }
    }
  },
  "size": 0
}'

    Response: {"facets":{"terms":{"unique":2,"total":2,"missing":0,"other":0}}}

    Response fields description:

    unique  -   unique terms count
    total   -   total documents count
    missing -   document with missing field value count
    other   -   documents with other field value count (might be not equal to 0 if terms.size request parameter is less than unique terms count, normally should be 0)

3) Deleting indices:

$  curl -XDELETE 'http://localhost:9200/twitter-2014.03.15-00,twitter-2014.03.15-01/'

4)  Getting CACHED unique terms count for @uid field:

$  curl -XPOST http://localhost:9200/twitter-2014.03.15-00,twitter-2014.03.15-01/_unique -d '{
  "facets": {
    "terms": {
      "terms": {
        "field": "@uid",
        "size": 10,
        "order": "count",
        "exclude": []
      },
      "facet_filter": {
        "fquery": {
          "query": {
            "filtered": {
              "query": {
                "bool": {
                  "should": [
                    {
                      "query_string": {
                        "query": "*"
                      }
                    }
                  ]
                }
              },
              "filter": {
                "bool": {
                  "must": [
                    {
                      "range": {
                        "@timestamp": {
                          "from": 1394841600000,
                          "to": "now"
                        }
                      }
                    }
                  ]
                }
              }
            }
          }
        }
      }
    }
  },
  "size": 0
}'

    Response: {"facets":{"terms":{"unique":2,"total":2,"missing":0,"other":0}}}

5)  Clearing cache and getting current unique terms count for @uid field:

$  curl -XPOST http://localhost:9200/twitter-2014.03.15-00,twitter-2014.03.15-01/_unique?clearCache=true -d '{
  "facets": {
    "terms": {
      "terms": {
        "field": "@uid",
        "size": 10,
        "order": "count",
        "exclude": []
      },
      "facet_filter": {
        "fquery": {
          "query": {
            "filtered": {
              "query": {
                "bool": {
                  "should": [
                    {
                      "query_string": {
                        "query": "*"
                      }
                    }
                  ]
                }
              },
              "filter": {
                "bool": {
                  "must": [
                    {
                      "range": {
                        "@timestamp": {
                          "from": 1394841600000,
                          "to": "now"
                        }
                      }
                    }
                  ]
                }
              }
            }
          }
        }
      }
    }
  },
  "size": 0
}'

    Response: {"error":"IndexMissingException[[twitter-2014.03.15-01] missing]","status":404}
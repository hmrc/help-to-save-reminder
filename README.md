help-to-save-reminder
=====================
Backend service for Help to Save Reminder.


Table of Contents
=================

* [About Help to Save Reminder](#about-help-to-save-reminder)
   * [Abbreviations](#abbreviations)
   * [Product Background](#product-background)
   * [Batch Processing](#htsUserSchedule-batchProcessing)
   * [Product Repos](#product-repos)
   * [Private Beta User Restriction](#private-beta-user-restriction)
* [Running and Testing](#running-and-testing)
   * [Running](#running)
   * [Unit tests](#unit-tests)
* [Endpoints](#endpoints)
* [License](#license)

About Help to Save Reminder
==========================

Abbreviations
-------------
| Key | Meaning |
|:----------------:|-------------|
|HoD| Head of Duty, HMRC legacy application |
|HtS| Help to Save |
|MDTP| HMRC Multi-channel Digital Tax Platform |
|ns&i| National Savings & Investments |

Product Background
------------------
The Prime Minister set out the government’s intention to bring forward a new Help to Save
(‘HtS’) scheme, to encourage people on low incomes to build up a “rainy day” fund.

Help to Save will target working families on the lowest incomes to help them build up their
savings. The scheme will be open to 3.5 million adults in receipt of Universal Credit with
minimum weekly household earnings equivalent to 16 hours at the National Living Wage, or those in receipt 
of Working Tax Credit.

A customer can deposit up to a maximum of £50 per month in the account. It will work by
providing a 50% government bonus on the highest amount saved into a HtS account. The
bonus is paid after two years with an option to save for a further two years, meaning that people
can save up to £2,400 and benefit from government bonuses worth up to £1,200. Savers will be
able to use the funds in any way they wish.

Batch Processing
------------------
The main aim of this service is to process HtS user schedules to send reminder emails to the registered HtS users
The user schedules are fetched based on the 'nextSendDate' field. Those schedules that have 'nextSendDate' as the current day 
or the day in the past will be fetched, for sending the reminder emails. 

Both already registered and the newly registering HtS users are given an option to opt in or opt out of this reminders service. Those who
opt in will be given a choice to receive the reminder email on 1st, 25th or both those days of the month. Users are also given
an option to cancel their subscription to the reminders service any time. 

The scheduler is based on Quartz Scheduler and the actual schedule times are managed by Quartz Cron expression that can be defined to run at 
a particular date and time of any day of the week, month or year.



Product Repos
-------------
The suite of repos connected with this Product are as follows:  

| Repo | Description |
|:-----|:------------|
| [help-to-save-reminder](https://github.com/hmrc/help-to-save-reminder) 
| [help-to-save-frontend](https://github.com/hmrc/help-to-save-frontend)                       | handles requests from browser for public digital journey |
| [help-to-save](https://github.com/hmrc/help-to-save)                                         | handles backend logic for HtS |
| [help-to-save-proxy](https://github.com/hmrc/help-to-save-proxy)                             | handles requests to services outside of MDTP |
| [help-to-save-api](https://github.com/hmrc/help-to-save-api)                                 | handles requests from third parties outside of MDTP |
| [help-to-save-stride-frontend](https://github.com/hmrc/help-to-save-stride-frontend)         | handles requests from browser for internal call centre journey |
| [help-to-save-stub](https://github.com/hmrc/help-to-save-stub)                               | provides endpoints for testing |
| [help-to-save-integration-tests](https://github.com/hmrc/help-to-save-integration-tests)     | contains system integration tests |
| [help-to-save-performance-tests](https://github.com/hmrc/help-to-save-performance-tests)     | contains load performance tests |
| [help-to-save-test-admin-frontend](https://github.com/hmrc/help-to-save-test-admin-frontend) | provides useful functions for testing |

This repo is the JSON create account interface schema between HMRC and NS&I:
- [help-to-save-apis](https://github.com/hmrc/help-to-save-apis/blob/master/1.0/create-account.request.schema.json)

This diagram shows a general picture of how the different services are connected to each other:
```                                                                                                                                                                              
                           browser - internal                    browser - public                                                                                        
                           call centre journey                   digital journey                                                                                         
                                     |                                  |                                                       
                                     |                                  |                                                       
                                     |                                  |                                                       
            +------------------------+----------------------------------+-----------------------+                               
            | MDTP                   |                                  |                       |                               
            |                        |                                  |                       |                               
            |        +---------------+--------------+   +---------------+--------------+        |                               
            |        |                              |   |                              |        |                               
            |        | help-to-save-stride-frontend |   |    help-to-save-frontend     |        |                               
            |        |                              |   |                              |        |                               
            |        +---------------+--------------+   +---------------+--------------+        |                               
            |                        |                                  |                       |                               
            |                        |                                  |                       |                               
            |                        +---------+              +---------+ ---------+            |                               
            |                                  |              |                    |            |                               
            |                          +-------+--------------+   +----------+----------------+ |
            |                          |                      |   |                           | |
 HoDs ------+--------------------------+    help-to-save |    |   |   help-to-save-reminder   | |    
            |                          |                      |   |                           | |
            |                          +-------+--------------+   +-------+-------------------+ |
            |                                  |        |                                       |
            |                       +----------+        +---------+                             |    
            |                       |                              |                            |
            |                       |                              |                            |
            |       +---------------+--------------+     +--------------+---------------+       |               
            |       |                              |     |                              |       |               
            |       |       help-to-save-api       |     |      help-to-save-proxy      |       |               
            |       |                              |     |                              |       |               
            |       +---------------+--------------+     +--------------+---------------+       |
            |                       |                                   |                       | 
            +-----------------------+-----------------------------------+-----------------------+                               
                                    |                                   |                                     
                                    |                                   |                                                         
                                    |                                   |                                                         
                                    |                                   |                                                         
                                    |                                   |                                                         
                              incoming requests                requests to third-party                                                               
                              from third-parties                    services                                                                       
```



Running and Testing
===================

Running
-------

Run `sbt run` on the terminal to start the service. The service runs on port 7004 by default.

Unit tests
----------
Run `sbt test` on the terminal to run the unit tests.


Endpoints
=========

| Path                              | Method | Description |
|:-----------------------------     |:-------|:------------|
| /gethtsuser/:nino                 | GET  | Fetches schedule for a NINO or 404 (NOT FOUND) if there is no account for that NINO  |
| /update-htsuser-entity            | POST | Checks and updates schedule and creates new schedule if none is found for that NINO |
| /delete-htsuser-entity            | POST | Deletes an schedule from the data repo |
| /update-htsuser-email             | POST | Updates email address in schedule for that NINO |
| /bouncedEmail/:callbackreference  | POST | Deletes schedule if the request contains 'PermanentBounce' event and logs request info for other types of events |
| /help-to-save-reminder/test-only/:noUsers/:emailPrefix/daysToReceive | GET | Creates the supplied number of entries in mongo with the email prefix (e.g. emailPrefix@hotmail.com) and the days that that user would receive the reminder. |       
|                                   |        |             |
| TestOnly                          |      |               |
|:-----------------------------     |:-------|:------------|
| /help-to-save-reminder/test-only/:noUsers/:emailPrefix/daysToReceive | GET | Creates the supplied number of entries in mongo with the email prefix (e.g. emailPrefix@hotmail.com) and the days that that user would receive the reminder. |       
|                                   |        |             |

Example request to test-only endpoint:
http://localhost:7008/help-to-save-reminder/test-only/populate-reminders/5/johndoe/daysToReceive?day=1&day=25
License 
=======
This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")



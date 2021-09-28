# Ruby Installation for Integration Verification Tests (IVT)

1. (Mac OS Env) Install homebrew: https://brew.sh/   

2. Install GNU Privacy Guard
    ```bash
    brew install gnupg gnupg2
    ```
    NOTE: This is dependent on Homebrew to be completely installed first.

3. Install Ruby

    Directions here: https://rvm.io/rvm/install. Be sure to install RVM stable with ruby. You only need to follow `Install GPG keys` and `Install RVM stable with ruby`.
   

4. Ensure you can call rvm
    ```bash
    rvm list
    ```
   
5. If ruby-2.6.5 is not listed in the script response above, run these install commands:  
    ```bash
    rvm install ruby-2.6.5
    rvm use --default ruby-2.6.5
    gem install bundler
    ```
    
6. Run this script in the terminal in the same directory as Gemfile
    ```bash
    bundle install
    ```
    NOTE: Ensure `gem install bundler` completed first. It should exit with code 0 at the end.

    
7. (Optional) If running in IntelliJ, configure this project as an RVM Ruby project:

    * Install the Ruby plugin: `IntelliJ IDEA > Preferences... > Plugins`
    * Configure Project: `File > Project Structure > Project Settings > Project` and select `RVM: ruby-2.6.5`.
    * Configure Module: `File > Project Structure > Project Settings > Modules` and reconfigure module with RVM Ruby.
    
    NOTE: Ensure that your Ruby versions match across terminal default, Gemfile, and Gemfile.lock. If using IntelliJ, the Ruby version in your module should match as well.


8. (Optional) To run tests locally, export these environment variables. Most of the values can be found in `.github/workflows/ci-workflow.yml`, with exceptions noted below.:
    - Export these environment variables:
      * KAFKA_BROKERS 
      * ELASTIC_URL 
      * ELASTIC_USER 
      * ELASTIC_PASSWORD - password for your ElasticSearch instance service credential
      * CLOUD_API_KEY - found in your password manager
      * SASL_PLAIN_PASSWORD - password for your Event Streams instance service credential
      * FLINK_URL 
      * APPID_TENANT 
      * APPID_URL 
      * OIDC_HRI_INTERNAL_CLIENT_ID
      * OIDC_HRI_INTERNAL_CLIENT_SECRET - secret field for your HRI internal application in AppId
      * OIDC_HRI_DATA_INTEGRATOR_CLIENT_ID
      * OIDC_HRI_DATA_INTEGRATOR_CLIENT_SECRET - secret field for your HRI data integrator application in AppId
      * APPID_HRI_AUDIENCE
      * APPID_FLINK_AUDIENCE
      * INPUT_TOPIC
      * OUTPUT_TOPIC
      * NOTIFICATION_TOPIC
      * INVALID_TOPIC
      * HRI_INGRESS_URL
      * HRI_SERVICE_URL
      * LOGDNA_URL
      * LOGDNA_INGESTION_KEY - Cloud LogDNA Ingestion Key
      * BRANCH_NAME - your git branch name
   

   - Install the IBM Cloud CLI and the Event Streams CLI. You can find the RESOURCE_GROUP in `.github/workflows/ci-workflow.yml` and the CLOUD_API_KEY in your password manager:
        ```bash
            curl -sL https://ibm.biz/idt-installer | bash
            bx login --apikey {CLOUD_API_KEY}
            bx target -g {RESOURCE_GROUP}
            bx plugin install event-streams
            bx es init
        ```
     Then select the number corresponding to the KAFKA_INSTANCE in `.github/workflows/ci-workflow.yml`.
   

   - Clone the `hri-flink-pipeline-core` repo (https://github.com/Alvearie/hri-flink-pipeline-core), then run the following command in that directory:
     
      ```./gradlew publishToMavenLocal```

      When that completes, run the following command in the `hri-flink-validation-fhir` directory:
     
     ```./gradlew clean build shadowJar --refresh-dependencies```
     

   - From within the top directory of this project, run the integration tests with:   
     ```rspec test/spec --tag ~@broken```
     
      If you want to run the high availability, performance or load tests, you must first run the following commands in the `hri-flink-validation-fhir` repo:
      ```bash
         ./gradlew build publishToMavenLocal
         ./gradlew copyNightlyTestDependencies -PcloudApiKey=$CLOUD_API_KEY
      ```

     The last step before running the tests is to install the `hri-test-helpers` gem locally. Run the following commands:
      ```bash
         gem install specific_install
         gem specific_install -l git@github.com/Alvearie/hri-test-helpers.git -b master
      ```
     Then, add the following line to Gemfile, but *do not commit this change to Github*:
     ```gem 'hri-test-helpers```
     
      Then run the tests with:
     
     ```rspec test/nightly/flink_validation_high_availability_spec.rb --tag ~@broken```
     
     The load test requires a bucket of auto-generated, large fhir records. The following command generates the records in the `test/test_data/synthea` directory.
     
     ```java -jar test/dependencies/synthea-with-dependencies.jar -p 100 -c test/test_data/synthea.properties```
     
     The Synthea records are automatically generated when the `copyNightlyTestDependencies` task is run. Alternatively,
     you can use the `generateSyntheaRecords` task to generate the records independently.
     
     The performance test requires a different bucket of static records stored in a zip file in COS. These records are 
     also automatically downloaded when the `copyNightlyTestDependencies` task is run. Alternitively, you can use the
     `downloadPerfTestRecords` task to download the records independently.
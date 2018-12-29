# Untis-Bot for FA3C Telegram group

## Building
1. JDK must be installed
2. Clone this repository
3. Run `gradlew build` on the command line
4. Output will be in `build/distributions`
5. To run the bot, unzip/untar the distribution and run `/bin/UntisBot` in the resulting folder.

Note: Telegram token for the bot must be put in `telegram-token` file in current working directory.

## Development in IntelliJ-IDEA
After cloning the repository simply do File > Open and select the `build.gradle.kts` file to import the project.
All dependencies will be automatically downloaded and applied to the project.
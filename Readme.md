# NetBird android client
## Testing
### requirements
We need the following software:
* Java 1.11. Usually comes with android studio
* android studio initialized with jdk and emulator (not covered here, is a req from android-client and react-native-app projects)
* gradle (https://gradle.org/install/)
* npm 1.18, yarn and nvm:
```shell
# download and install nvm https://github.com/nvm-sh/nvm
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.3/install.sh | bash
#
nvm install v19
nvm use v19
npm install -g yarn
npm install -g npx
```

### run locally
1. close all repositories:
> assuming you use a path like ~/projects locally
```shell
mkdir ~/projects
cd projects
# clone netbird repo
git clone git@github.com:netbirdio/netbird.git
# clone react native app repo
git clone git@github.com:netbirdio/react-native-app.git
```
2. Checkout the repositories to the branches you want to test. If you want the latest, check the status information on your IDE or on https://github.com and verify the branch list and commit history.
3. export JDK and Android home vars, on macOS they are: (please contribute with Linux equivalent)
```shell
# replace <USERNAME> with your name
export ANDROID_HOME=/Users/<USERNAME>/Library/Android/sdk
export JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home
```
4. Install NDK and CMake
```shell
cd ~/projects/react-native-app
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --install "ndk;23.1.7779620"
```
5. Build the gomobile lib and the android client lib:
````shell
cd ~/projects/react-native-app
bash -x build-android-lib.sh ~/projects/netbird
````
6. Install the react native app dependencies
```shell
yarn install
yarn add file:./react/netbird-lib
```
7. Run the dev version
```shell
yarn start
```
8. select `a` to install it on your android phone or emulator

### generate debug bundle
Follow the steps to run locally until the step 5 then run the following steps:
1. run npx from react native app repo
```shell
cd ~/projects/react-native-app
npx react-native bundle --platform android --dev false --entry-file index.js --bundle-output android/app/src/main/assets/index.android.bundle --assets-dest android/app/src/main/res
```
2. run gradlew
```shell
cd ~/projects/react-native-app/android
./gradlew bundleDebug
```
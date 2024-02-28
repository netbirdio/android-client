import {Platform, StatusBar, Linking, Dimensions} from 'react-native';
import {InAppBrowser} from 'react-native-inappbrowser-reborn';

const sleep = (timeout: number) =>
  new Promise<void>(resolve => setTimeout(resolve, timeout));

export const openLink = async (url: string, animated = true) => {
  try {
    const {width, height} = Dimensions?.get('window');
    if (await InAppBrowser.isAvailable()) {
      // A delay to change the StatusBar when the browser is opened
      const delay = animated && Platform.OS === 'ios' ? 400 : 0;
      setTimeout(() => StatusBar.setBarStyle('light-content'), delay);
      const result = await InAppBrowser.open(url, {
        // iOS Properties
        dismissButtonStyle: 'cancel',
        preferredBarTintColor: '#453AA4',
        preferredControlTintColor: 'white',
        readerMode: true,
        animated,
        modalPresentationStyle: 'formSheet',
        modalTransitionStyle: 'flipHorizontal',
        modalEnabled: true,
        enableBarCollapsing: true,
        formSheetPreferredContentSize: {
          width: width - width / 6,
          height: height - height / 6,
        },
        // Android Properties
        showTitle: true,
        toolbarColor: '#F2F2F2',
        secondaryToolbarColor: '#FFFFFF',
        navigationBarColor: '#FFFFFF',
        navigationBarDividerColor: '#FFFFFF',
        enableUrlBarHiding: false,
        enableDefaultShare: false,
        forceCloseOnRedirection: false,
        animations: {
          startEnter: 'slide_in_right',
          startExit: 'slide_out_left',
          endEnter: 'slide_in_left',
          endExit: 'slide_out_right',
        },
        hasBackButton: true,
        browserPackage: undefined,
        showInRecents: false,
        includeReferrer: false,
      });
    } else {
      Linking.openURL(url);
    }
  } catch (error) {
    await sleep(50);
    const errorMessage = (error as Error).message || (error as string);
  }
};

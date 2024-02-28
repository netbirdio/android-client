import React from 'react';
import { Platform, StyleSheet, Text, View } from 'react-native';
import { colors } from '../../theme';

interface Props {
  textCount: number;
  textTotal: number;
}

const styles = StyleSheet.create({
  androidHiddenBorder: {
    borderStyle: 'solid',
    borderLeftWidth: 3,
    borderLeftColor: colors.bottomPeersBgColor,
    borderRightWidth: 3,
    borderRightColor: colors.bottomPeersBgColor,
    position: 'absolute',
    width: '100%',
    left: 0,
    right: 0,
    top: 15 + 38,
    bottom: 0,
    zIndex: 1,
  },
  peersHeader: {
    color: 'rgba(0, 0, 0, 0.45)', 
    fontSize: 16,
    lineHeight: 24,
    fontWeight: "500"
  },
  SeparatorBar: {
    width: 100,
    height: 6,
    borderRadius: 3,
    backgroundColor: 'rgba(217, 217, 217, 1)',
    margin: 16
  }
});

const BottomPeers: React.FC<Props> = ({ textCount, textTotal }) => {
  const backgroundColor =
    Platform.OS == 'ios' ? colors.bottomPeersBgColor : 'transparent';

  return (
    <View
      style={{
        alignItems: 'center',
        width: '100%',
        overflow: 'hidden',
        display: 'flex',
        paddingTop: 15,
      }}>
      <View style={[styles.androidHiddenBorder]}></View>
      <View
        style={{
          width: '100%',
          top: 0,
          paddingTop: 6,
          paddingLeft: 3,
          paddingRight: 3,
          shadowOffset: {
            width: 0,
            height: -3,
          },
          shadowColor: '#171717',
          shadowRadius: 3,
          shadowOpacity: 0.1,
          elevation: 3,

          borderTopStartRadius: 35,
          borderTopEndRadius: 35,
          borderBottomStartRadius: 0,
          borderBottomEndRadius: 0,
          borderLeftColor: colors.bottomPeersBgColor,
          backgroundColor: backgroundColor,
          //overflow: 'hidden',
        }}>
        <View
          style={{
            backgroundColor: colors.bottomPeersBgColor,
            alignItems: 'center',
            borderTopStartRadius: 35,
            borderTopEndRadius: 35,
            borderBottomStartRadius: 0,
            borderBottomEndRadius: 0,
            paddingBottom: 15,
          }}>
          <View style={styles.SeparatorBar}></View>
          <Text testID="peerCountText">
            <Text style={[styles.peersHeader, {fontWeight: '700' }]}>
              {textCount} of {textTotal}{' '}
            </Text>
            <Text style={[styles.peersHeader, {fontWeight: '500' }]}>Peers connected</Text>
          </Text>
        </View>
      </View>
    </View>
  );
};

export default BottomPeers;

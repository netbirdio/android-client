import React, {useEffect, useState} from 'react';
import {
  FlatList,
  Image,
  Text,
  ToastAndroid,
  TouchableOpacity,
  View,
  Dimensions,
  Linking,
  StyleSheet,
} from 'react-native';
import {useSelector} from 'react-redux';
import {Peer} from '../../store/app-status/types';
import {colors} from '../../theme';
import {RootState} from 'typesafe-actions';
import {BottomSheetVirtualizedList} from '@gorhom/bottom-sheet';
import Input from '@components/Input';
import {Menu, MenuDivider, MenuItem} from 'react-native-material-menu';
import Clipboard from '@react-native-clipboard/clipboard';
import {Button} from '@components';

const windowWidth = Dimensions?.get('window').width;
const windowHeight = Dimensions?.get('window').height;

interface Props {
  bottomSheet?: boolean;
  onClose?: () => void;
}

enum StatusConnection {
  disconnected = 'disconnected',
  connected = 'connected',
}

interface PeerList extends Peer {
  key: number;
}

const ListPeers: React.FC<Props> = ({bottomSheet, onClose}) => {
  const peers = useSelector((state: RootState) => state.appStatus.peers);

  const [filteredPeers, setFilteredPeers] = useState<PeerList[]>([]);
  const [statusToFilter, setStatusToFilter] = useState<StatusConnection | null>(
    null,
  );
  const [textToFilter, setTextToFilter] = useState<string>('');
  const [filterMenuVisible, setFilterMenuVisible] = useState<boolean>(false);

  useEffect(() => {
    filterPeers(peers.data);
  }, [peers, statusToFilter, textToFilter]);

  const filterPeers = (peersToFilter: Peer[]) => {
    setFilteredPeers(
      peersToFilter
        .filter(
          (p: Peer) =>
            (statusToFilter === null ||
              p.connStatus.toLowerCase() === statusToFilter) &&
            (textToFilter.trim() === '' ||
              p.domainName.toLowerCase().includes(textToFilter.toLowerCase()) ||
              p.ip.toLowerCase().includes(textToFilter.toLowerCase())),
        )
        .map((p, i) => ({...p, key: i})),
    );
  };

  const renderItem = (peer: PeerList) => {
    const connected = 'rgba(124, 179, 5, 1)';
    const disconnected = '#aaaaaa';
    const domainName = peer.domainName;

    return (
      <TouchableOpacity
        onLongPress={() => {
          Clipboard.setString(domainName);
          ToastAndroid.show('Domain name copied!', ToastAndroid.SHORT);
        }}>
        <View
          style={{
            padding: 15,
            flex: 1,
            flexDirection: 'row',
            backgroundColor: 'rgba(0, 0, 0, 0.02)',
            width: '100%',
          }}>
          <View style={{justifyContent: 'center', marginRight: 5}}>
            <View
              style={{
                height: 40,
                borderRadius: 42,
                width: 8,
                margin: 8,
                backgroundColor:
                  peer.connStatus.toLowerCase() === StatusConnection.connected
                    ? connected
                    : disconnected,
              }}
            />
          </View>
          <View
            style={{
              flex: 1,
              flexDirection: 'column',
              justifyContent: 'center',
              gap: 8,
            }}>
            <Text style={styles.text}>{peer.domainName}</Text>
            <Text style={styles.text}>{peer.ip}</Text>
          </View>
          <View style={{justifyContent: 'center'}}>
            <Text style={[styles.text, {marginRight: 15}]}>
              {peer.connStatus.toLowerCase() === StatusConnection.connected
                ? 'connected'
                : 'disconnected'}
            </Text>
          </View>
        </View>
      </TouchableOpacity>
    );
  };

  const onChangeTextToFilter = (text: string) => {
    setTextToFilter(text);
  };

  const onSelectMenuFilter = (status: StatusConnection | null) => {
    setStatusToFilter(status);
    setFilterMenuVisible(false);
  };

  const renderMenuItem = (
    text: string,
    onPress: () => void,
    statusToEnable: StatusConnection | null,
  ) => {
    return (
      <MenuItem
        onPress={() => onPress()}
        style={{
          backgroundColor:
            statusToFilter === statusToEnable ? '#dddddd' : 'transparent',
        }}
        textStyle={{color: colors.textDefault, fontFamily: 'Roboto'}}>
        {text}
      </MenuItem>
    );
  };

  return (
    <>
      <View style={{padding: 24, marginTop: 20}}>
        <TouchableOpacity
          hitSlop={{top: 20, bottom: 20, left: 20, right: 20}}
          onPress={() => (onClose ? onClose() : false)}
          style={{
            position: 'absolute',
            right: 20,
            top: -30,
            paddingTop: 20,
            paddingLeft: 10,
            paddingRight: 10,
            paddingBottom: 10,
          }}>
          <Image
            source={require('@assets/images/close-slider-4x.png')}
            style={{width: 15, height: 15, resizeMode: 'contain'}}
          />
        </TouchableOpacity>
        {peers?.total === 0 ? (
          <View
            style={{
              flexDirection: 'column',
              alignItems: 'center',
              marginHorizontal: 8,
            }}>
            <Image
              source={require('@assets/images/icon-empty-box.png')}
              style={{
                width: windowWidth / 3,
                height: windowHeight / 7,
                resizeMode: 'contain',
              }}
            />
            <Text
              style={{
                marginBottom: 70,
                marginTop: 40,
                  color: colors.textDefault,
              }}>
              It looks like there are no machines that {'\n'} you can connect
              to...
            </Text>
            <Button
              onPress={() =>
                Linking.openURL('https://netbird.io/docs/overview/acls')
              }
              text={'Learn why'}
            />
          </View>
        ) : (
          <>
            <View
              style={{marginBottom: 32, alignItems: 'center', width: '100%'}}>
              <Text>
                <Text
                  style={{
                    fontWeight: '700',
                    color: 'rgba(0, 0, 0, 0.45)',
                    fontSize: 16,
                  }}>
                  {peers?.connected} of {peers?.total}{' '}
                </Text>
                <Text
                  style={{
                    fontWeight: '500',
                    color: 'rgba(0, 0, 0, 0.45)',
                    fontSize: 16,
                  }}>
                  Peers connected
                </Text>
              </Text>
            </View>
            <View style={{flexDirection: 'row', alignItems: 'center'}}>
              <View style={styles.mainInputStyle}>
                <Input
                  stylesMask={{borderRadius: 8}}
                  value={textToFilter}
                  placeHolder={'search peers'}
                  onChangeText={(text: string) => onChangeTextToFilter(text)}
                />
                <TouchableOpacity
                  style={{
                    position: 'absolute',
                    right: 8,
                    top: 0,
                    paddingTop: 10,
                    paddingLeft: 10,
                    paddingRight: 10,
                    paddingBottom: 10,
                  }}>
                  <Image
                    source={require('@assets/images/search-4x.png')}
                    style={{width: 15, height: 15, resizeMode: 'contain'}}
                  />
                </TouchableOpacity>
              </View>
              <View style={{flex: 0}}>
                <Menu
                  style={{marginTop: 30}}
                  visible={filterMenuVisible}
                  onRequestClose={() => setFilterMenuVisible(false)}
                  anchor={
                    <TouchableOpacity
                      style={{marginRight: 5, marginLeft: 25}}
                      hitSlop={{top: 20, bottom: 20, left: 10, right: 20}}
                      onPress={() => setFilterMenuVisible(true)}>
                      <Image
                        source={require('../../assets/images/icon-filter-4x.png')}
                        style={{width: 18, height: 18, resizeMode: 'contain'}}
                      />
                    </TouchableOpacity>
                  }>
                  {renderMenuItem('All', () => onSelectMenuFilter(null), null)}
                  <MenuDivider />
                  {renderMenuItem(
                    'Connected',
                    () => onSelectMenuFilter(StatusConnection.connected),
                    StatusConnection.connected,
                  )}
                  {renderMenuItem(
                    'Disconnected',
                    () => onSelectMenuFilter(StatusConnection.disconnected),
                    StatusConnection.disconnected,
                  )}
                </Menu>
              </View>
            </View>
          </>
        )}
      </View>
      {bottomSheet ? (
        <BottomSheetVirtualizedList
          initialNumToRender={5}
          keyExtractor={(item: PeerList) => item.key}
          style={{flex: 1, width: '100%', backgroundColor: colors.bgColor}}
          ItemSeparatorComponent={() => <View style={{height: 20}} />}
          data={filteredPeers}
          getItemCount={_data => filteredPeers.length}
          getItem={(_data, index) => filteredPeers[index]}
          renderItem={({item, index, separators}) => renderItem(item)}
        />
      ) : (
        <View
          style={{
            flex: 1,
            alignItems: 'center',
            width: '100%',
          }}>
          <FlatList
            style={{flex: 1, width: '100%', backgroundColor: colors.bgColor}}
            ItemSeparatorComponent={() => <View style={{height: 20}} />}
            data={filteredPeers}
            nestedScrollEnabled={true}
            renderItem={({item}) => renderItem(item)}
          />
        </View>
      )}
    </>
  );
};

export default React.memo(ListPeers);

const styles = StyleSheet.create({
  placeholder: {
    marginTop: 25,
    fontStyle: 'italic',
    fontSize: 28,
    color: 'red',
  },
  mainInputStyle: {
    flex: 1,
    borderRadius: 22,
  },
  text: {
    color: 'rgba(0, 0, 0, 0.45)',
    fontFamily: 'Roboto',
    fontWeight: '400',
    fontSize: 14,
  },
});

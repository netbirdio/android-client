import React from 'react';
import { Text, TouchableOpacity, View, ViewStyle, FlexAlignType, Dimensions } from 'react-native';
import Lottie from 'lottie-react-native';

const windowWidth = Dimensions?.get('window')?.width;

interface Props {
  text: string;
  id?: string;
  outline?: boolean;
  loading?: boolean;
  imagePrefix?: () => JSX.Element;
  onPress: () => void;
}

const buttonStyles = {
  touchableOpacity: {
    borderWidth: 1,
    borderColor: '#f35e32',
    borderRadius: 2,
    padding: 8,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#f68330',
  } as ViewStyle,
  touchableOpacityOutline: {
    borderWidth: 1,
    borderColor: '#f35e32',
    borderRadius: 2,
    padding: 8,
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    
  } as ViewStyle,
  content: {
    width: (windowWidth * 0.7),
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
  } as ViewStyle,
};

const Button: React.FC<Props> = ({
  text,
  outline,
  loading,
  imagePrefix,
  onPress,
}) => {

  const showLoading = (loading: boolean | undefined) => {
    if (loading) {
      return (
        <View style={{ width: 50, height: '100%' }}>
          <Lottie
            source={require('../../assets/animations/loading.json')}
            autoPlay
            loop
          />
        </View>
      );
    }
    return null;
  };

  return (
    <>
      {!outline ? (
        <TouchableOpacity onPress={onPress}>
          <View style={buttonStyles.touchableOpacity}>
            <View style={buttonStyles.content}>
              {imagePrefix && imagePrefix()}
              <Text style={{ fontSize: 14, lineHeight: 22, color: '#ffffff' }}>
                {text}
              </Text>
              {showLoading(loading)}
            </View>
          </View>
        </TouchableOpacity>
      ) : (
        <TouchableOpacity onPress={onPress}>
          <View style={buttonStyles.touchableOpacityOutline}>
            {imagePrefix && imagePrefix()}
            <Text style={{ fontSize: 14, lineHeight: 22, color: '#F35E32' }}>{text}</Text>
          </View>
        </TouchableOpacity>
      )}
    </>
  );
};

export default React.memo(Button);

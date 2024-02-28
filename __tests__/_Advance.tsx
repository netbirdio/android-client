import React from 'react';
import {shallow} from 'enzyme';
import { useSelector} from 'react-redux';
import {RouteProp} from '@react-navigation/native';
import Advance from '../src/screens/Advance';
import {RootStackParamList} from '@navigation/RootStackParamList';
import configureStore from 'redux-mock-store';
import {Provider} from 'react-redux';

jest.mock('react-redux', () => ({
  ...jest.requireActual('react-redux'),
  useSelector: jest.fn(),
  useDispatch: jest.fn(),
}));
jest.mock('@react-navigation/native', () => ({
  useFocusEffect: jest.fn(),
}));

const navigation = {
  navigate: jest.fn(),
  goBack: jest.fn(),
} as any;

const advanceState = {
  inUse: true,
  saving: false,
};
const route = {
  key: 'test-key',
  name: 'Advance',
} as RouteProp<RootStackParamList, 'Advance'>;

const mockStore = configureStore();
const store = mockStore({});

describe('Advance Screen', () => {
  it('should render correctly when pre-shared key is in use', () => {
    const useSelectorMock = useSelector as jest.Mock;
    useSelectorMock.mockReturnValue({...advanceState, inUse: true});
    const wrapper = shallow(
      <Provider store={store}>
        <Advance navigation={navigation} route={route} />
      </Provider>,
    );
    const textComponents = wrapper.find('Text');

    expect(textComponents.at(0).contains('Pre-shared key is in use'));
    expect(
      textComponents
        .at(1)
        .contains('Make sure your other peers have set the same key.'),
    );
    expect(textComponents.at(2).contains('**********'));
    expect(wrapper).toMatchSnapshot();
  });

  it('should render correctly when pre-shared key is not in use', () => {
    const useSelectorMock = useSelector as jest.Mock;
    useSelectorMock.mockReturnValue({...advanceState, inUse: false});

    const wrapper = shallow(
      <Provider store={store}>
        <Advance navigation={navigation} route={route} />
      </Provider>,
    );
    const textComponents = wrapper.find('Text');

    expect(textComponents.at(0).contains('Add a pre-shared key'));
    expect(
      textComponents
        .at(1)
        .contains(
          'You will only communicate with peers that use the same key.',
        ),
    );
    expect(wrapper).toMatchSnapshot();
  });
});

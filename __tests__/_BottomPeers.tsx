import React from 'react';
import { shallow } from 'enzyme';
import BottomPeers from '../src/components/BottomPeers';

describe('BottomPeers', () => {
  const props = {
    textCount: 2,
    textTotal: 5,
  };

  it('renders without crashing', () => {
    const wrapper = shallow(<BottomPeers {...props} />);
    expect(wrapper.exists()).toBe(true);
  });

  it('displays the correct peer count', () => {
    const wrapper = shallow(<BottomPeers {...props} />);
    const peerCountText = wrapper
      .findWhere(node => node.prop('testID') === 'peerCountText')
      .first()
      .prop('children')
      .reduce((acc: string, child: { props: { children: any; }; }) => {
        if (typeof child === 'string') {
          return acc + child;
        }
        if (typeof child === 'object' && typeof child.props.children === 'string') {
          return acc + child.props.children;
        }
        return acc;
      }, '');

    expect(peerCountText).toBe('Peers connected');
  });

  it('matches snapshot', () => {
    const wrapper = shallow(<BottomPeers {...props} />);
    expect(wrapper).toMatchSnapshot();
  });
});

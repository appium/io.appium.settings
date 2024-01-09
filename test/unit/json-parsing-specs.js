import chai from 'chai';
import { SettingsApp } from '../../lib/client';

const should = chai.should();


describe('parseJsonData', function () {
  const client = new SettingsApp({});

  it('should parse JSON received from broadcast output', function () {
    const broadcastOutput = `
    Broadcasting: Intent { act=io.appium.settings.sms.read flg=0x400000 (has extras) }
    Broadcast completed: result=-1, data="{"items":[{"id":"2","address":"+123456789","date":"1581936422203","read":"0","status":"-1","type":"1","body":"\\"text message2\\""},{"id":"1","address":"+123456789","date":"1581936382740","read":"0","status":"-1","type":"1","body":"\\"text message\\""}],"total":2}"
    `;
    const {items, total} = client._parseJsonData(broadcastOutput, '');
    items.length.should.eql(2);
    total.should.eql(2);
  });
  it('should parse JSON received from broadcast output having extras', function () {
    const broadcastOutput = `
    Broadcasting: Intent { act=io.appium.settings.sms.read flg=0x400000 (has extras) }
    Broadcast completed: result=-1, data="{"items":[{"id":"2","address":"+123456789","date":"1581936422203","read":"0","status":"-1","type":"1","body":"\\"text message2\\""},{"id":"1","address":"+123456789","date":"1581936382740","read":"0","status":"-1","type":"1","body":"\\"text message\\""}],"total":2}", extras: Bundle[mParcelledData.dataSize=52]
    `;
    const {items, total} = client._parseJsonData(broadcastOutput, '');
    items.length.should.eql(2);
    total.should.eql(2);
  });
  it('should throw an error if json retrieval fails', function () {
    const broadcastOutput = `
    Broadcasting: Intent { act=io.appium.settings.sms.read flg=0x400000 (has extras) }
    Broadcast completed: result=0, data="{"items":[{"id":"2","address":"+123456789","date":"1581936422203","read":"0","status":"-1","type":"1","body":"\\"text message2\\""},{"id":"1","address":"+123456789","date":"1581936382740","read":"0","status":"-1","type":"1","body":"\\"text message\\""}],"total":2}"
    `;
    should.throw(() => client._parseJsonData(broadcastOutput, ''));
  });
  it('should throw an error if json parsing fails', function () {
    const broadcastOutput = `
    Broadcasting: Intent { act=io.appium.settings.sms.read flg=0x400000 (has extras) }
    Broadcast completed: result=-1, data="{24324"
    `;
    should.throw(() => client._parseJsonData(broadcastOutput, ''));
  });
});

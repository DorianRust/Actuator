package org.tron.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.math.BigInteger;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.Commons;
import org.tron.common.utils.StringUtil;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.ExchangeCapsule;
import org.tron.core.capsule.TransactionResultCapsule;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.core.exception.ItemNotFoundException;
import org.tron.core.store.AccountStore;
import org.tron.core.store.AssetIssueStore;
import org.tron.core.store.DynamicPropertiesStore;
import org.tron.core.store.ExchangeStore;
import org.tron.core.store.ExchangeV2Store;
import org.tron.core.utils.TransactionUtil;
import org.tron.protos.Contract.ExchangeInjectContract;
import org.tron.protos.Protocol.Transaction.Result.code;

@Slf4j(topic = "actuator")
public class ExchangeInjectActuator extends AbstractActuator {

  ExchangeInjectActuator(final Any contract,
      final AccountStore accountStore, final AssetIssueStore assetIssueStore,
      final DynamicPropertiesStore dynamicStore,final ExchangeStore exchangeStore, ExchangeV2Store exchangeV2Store) {
    super(contract, accountStore, assetIssueStore, dynamicStore, exchangeStore, exchangeV2Store);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    long fee = calcFee();
    try {
      final ExchangeInjectContract exchangeInjectContract = this.contract
          .unpack(ExchangeInjectContract.class);
      AccountCapsule accountCapsule = accountStore
          .get(exchangeInjectContract.getOwnerAddress().toByteArray());

      ExchangeCapsule exchangeCapsule;
      exchangeCapsule = Commons.getExchangeStoreFinal(dynamicStore, exchangeStore, exchangeV2Store).
          get(ByteArray.fromLong(exchangeInjectContract.getExchangeId()));
      byte[] firstTokenID = exchangeCapsule.getFirstTokenId();
      byte[] secondTokenID = exchangeCapsule.getSecondTokenId();
      long firstTokenBalance = exchangeCapsule.getFirstTokenBalance();
      long secondTokenBalance = exchangeCapsule.getSecondTokenBalance();

      byte[] tokenID = exchangeInjectContract.getTokenId().toByteArray();
      long tokenQuant = exchangeInjectContract.getQuant();

      byte[] anotherTokenID;
      long anotherTokenQuant;

      if (Arrays.equals(tokenID, firstTokenID)) {
        anotherTokenID = secondTokenID;
        anotherTokenQuant = Math
            .floorDiv(Math.multiplyExact(secondTokenBalance, tokenQuant), firstTokenBalance);
        exchangeCapsule.setBalance(firstTokenBalance + tokenQuant,
            secondTokenBalance + anotherTokenQuant);
      } else {
        anotherTokenID = firstTokenID;
        anotherTokenQuant = Math
            .floorDiv(Math.multiplyExact(firstTokenBalance, tokenQuant), secondTokenBalance);
        exchangeCapsule.setBalance(firstTokenBalance + anotherTokenQuant,
            secondTokenBalance + tokenQuant);
      }

      long newBalance = accountCapsule.getBalance() - calcFee();
      accountCapsule.setBalance(newBalance);

      if (Arrays.equals(tokenID, "_".getBytes())) {
        accountCapsule.setBalance(newBalance - tokenQuant);
      } else {
        accountCapsule.reduceAssetAmountV2(tokenID, tokenQuant, dynamicStore, assetIssueStore);
      }

      if (Arrays.equals(anotherTokenID, "_".getBytes())) {
        accountCapsule.setBalance(newBalance - anotherTokenQuant);
      } else {
        accountCapsule.reduceAssetAmountV2(anotherTokenID, anotherTokenQuant, dynamicStore, assetIssueStore);
      }
      accountStore.put(accountCapsule.createDbKey(), accountCapsule);

      Commons.putExchangeCapsule(exchangeCapsule, dynamicStore, exchangeStore, exchangeV2Store, assetIssueStore);

      ret.setExchangeInjectAnotherAmount(anotherTokenQuant);
      ret.setStatus(fee, code.SUCESS);
    } catch (ItemNotFoundException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
    return true;
  }

  @Override
  public boolean validate() throws ContractValidateException {
    if (this.contract == null) {
      throw new ContractValidateException("No contract!");
    }
    if (accountStore == null || dynamicStore == null) {
      throw new ContractValidateException("No account store or dynamic store!");
    }
    if (!this.contract.is(ExchangeInjectContract.class)) {
      throw new ContractValidateException(
          "contract type error,expected type [ExchangeInjectContract],real type[" + contract
              .getClass() + "]");
    }
    final ExchangeInjectContract contract;
    try {
      contract = this.contract.unpack(ExchangeInjectContract.class);
    } catch (InvalidProtocolBufferException e) {
      throw new ContractValidateException(e.getMessage());
    }

    byte[] ownerAddress = contract.getOwnerAddress().toByteArray();
    String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);

    if (!Commons.addressValid(ownerAddress)) {
      throw new ContractValidateException("Invalid address");
    }

    if (!accountStore.has(ownerAddress)) {
      throw new ContractValidateException("accountStore[" + readableOwnerAddress + "] not exists");
    }

    AccountCapsule accountCapsule = accountStore.get(ownerAddress);

    if (accountCapsule.getBalance() < calcFee()) {
      throw new ContractValidateException("No enough balance for exchange inject fee!");
    }

    ExchangeCapsule exchangeCapsule;
    try {
      exchangeCapsule = Commons.getExchangeStoreFinal(dynamicStore, exchangeStore, exchangeV2Store).
          get(ByteArray.fromLong(contract.getExchangeId()));

    } catch (ItemNotFoundException ex) {
      throw new ContractValidateException("Exchange[" + contract.getExchangeId() + "] not exists");
    }

    if (!accountCapsule.getAddress().equals(exchangeCapsule.getCreatorAddress())) {
      throw new ContractValidateException("accountStore[" + readableOwnerAddress + "] is not creator");
    }

    byte[] firstTokenID = exchangeCapsule.getFirstTokenId();
    byte[] secondTokenID = exchangeCapsule.getSecondTokenId();
    long firstTokenBalance = exchangeCapsule.getFirstTokenBalance();
    long secondTokenBalance = exchangeCapsule.getSecondTokenBalance();

    byte[] tokenID = contract.getTokenId().toByteArray();
    long tokenQuant = contract.getQuant();

    byte[] anotherTokenID;
    long anotherTokenQuant;

    if (dynamicStore.getAllowSameTokenName() == 1) {
      if (!Arrays.equals(tokenID, "_".getBytes()) && !TransactionUtil.isNumber(tokenID)) {
        throw new ContractValidateException("token id is not a valid number");
      }
    }

    if (!Arrays.equals(tokenID, firstTokenID) && !Arrays.equals(tokenID, secondTokenID)) {
      throw new ContractValidateException("token id is not in exchange");
    }

    if (firstTokenBalance == 0 || secondTokenBalance == 0) {
      throw new ContractValidateException("Token balance in exchange is equal with 0,"
          + "the exchange has been closed");
    }

    if (tokenQuant <= 0) {
      throw new ContractValidateException("injected token quant must greater than zero");
    }

    BigInteger bigFirstTokenBalance = new BigInteger(String.valueOf(firstTokenBalance));
    BigInteger bigSecondTokenBalance = new BigInteger(String.valueOf(secondTokenBalance));
    BigInteger bigTokenQuant = new BigInteger(String.valueOf(tokenQuant));
    long newTokenBalance, newAnotherTokenBalance;
    if (Arrays.equals(tokenID, firstTokenID)) {
      anotherTokenID = secondTokenID;
//      anotherTokenQuant = Math
//          .floorDiv(Math.multiplyExact(secondTokenBalance, tokenQuant), firstTokenBalance);
      anotherTokenQuant = bigSecondTokenBalance.multiply(bigTokenQuant)
          .divide(bigFirstTokenBalance).longValueExact();
      newTokenBalance = firstTokenBalance + tokenQuant;
      newAnotherTokenBalance = secondTokenBalance + anotherTokenQuant;
    } else {
      anotherTokenID = firstTokenID;
//      anotherTokenQuant = Math
//          .floorDiv(Math.multiplyExact(firstTokenBalance, tokenQuant), secondTokenBalance);
      anotherTokenQuant = bigFirstTokenBalance.multiply(bigTokenQuant)
          .divide(bigSecondTokenBalance).longValueExact();
      newTokenBalance = secondTokenBalance + tokenQuant;
      newAnotherTokenBalance = firstTokenBalance + anotherTokenQuant;
    }

    if (anotherTokenQuant <= 0) {
      throw new ContractValidateException("the calculated token quant  must be greater than 0");
    }

    long balanceLimit = dynamicStore.getExchangeBalanceLimit();
    if (newTokenBalance > balanceLimit || newAnotherTokenBalance > balanceLimit) {
      throw new ContractValidateException("token balance must less than " + balanceLimit);
    }

    if (Arrays.equals(tokenID, "_".getBytes())) {
      if (accountCapsule.getBalance() < (tokenQuant + calcFee())) {
        throw new ContractValidateException("balance is not enough");
      }
    } else {
      if (!accountCapsule.assetBalanceEnoughV2(tokenID, tokenQuant, dynamicStore)) {
        throw new ContractValidateException("token balance is not enough");
      }
    }

    if (Arrays.equals(anotherTokenID, "_".getBytes())) {
      if (accountCapsule.getBalance() < (anotherTokenQuant + calcFee())) {
        throw new ContractValidateException("balance is not enough");
      }
    } else {
      if (!accountCapsule.assetBalanceEnoughV2(anotherTokenID, anotherTokenQuant, dynamicStore)) {
        throw new ContractValidateException("another token balance is not enough");
      }
    }

    return true;
  }


  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(ExchangeInjectContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }

}

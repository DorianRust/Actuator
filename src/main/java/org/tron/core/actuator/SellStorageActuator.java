package org.tron.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.tron.common.utils.Commons;
import org.tron.common.utils.StringUtil;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.TransactionResultCapsule;
import org.tron.core.db.StorageMarket;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.core.store.AccountStore;
import org.tron.core.store.DynamicPropertiesStore;
import org.tron.protos.Contract.SellStorageContract;
import org.tron.protos.Protocol.Transaction.Result.code;

@Slf4j(topic = "actuator")
public class SellStorageActuator extends AbstractActuator {

  private StorageMarket storageMarket;

  SellStorageActuator(Any contract, AccountStore accountStore, DynamicPropertiesStore dynamicPropertiesStore) {
    super(contract, accountStore, dynamicPropertiesStore);
    storageMarket = new StorageMarket(accountStore, dynamicPropertiesStore);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    long fee = calcFee();
    final SellStorageContract sellStorageContract;
    try {
      sellStorageContract = contract.unpack(SellStorageContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }

    AccountCapsule accountCapsule = accountStore
        .get(sellStorageContract.getOwnerAddress().toByteArray());

    long bytes = sellStorageContract.getStorageBytes();

    storageMarket.sellStorage(accountCapsule, bytes);

    ret.setStatus(fee, code.SUCESS);

    return true;
  }


  @Override
  public boolean validate() throws ContractValidateException {
    if (this.contract == null) {
      throw new ContractValidateException("No contract!");
    }
    if (dynamicStore == null || accountStore == null) {
      throw new ContractValidateException("No account store or dynamic store!");
    }
    if (!contract.is(SellStorageContract.class)) {
      throw new ContractValidateException(
          "contract type error,expected type [SellStorageContract],real type[" + contract
              .getClass() + "]");
    }

    final SellStorageContract sellStorageContract;
    try {
      sellStorageContract = this.contract.unpack(SellStorageContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
    byte[] ownerAddress = sellStorageContract.getOwnerAddress().toByteArray();
    if (!Commons.addressValid(ownerAddress)) {
      throw new ContractValidateException("Invalid address");
    }

    AccountCapsule accountCapsule = accountStore.get(ownerAddress);
    if (accountCapsule == null) {
      String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
      throw new ContractValidateException(
          "Account[" + readableOwnerAddress + "] not exists");
    }

    long bytes = sellStorageContract.getStorageBytes();
    if (bytes <= 0) {
      throw new ContractValidateException("bytes must be positive");
    }

    long currentStorageLimit = accountCapsule.getStorageLimit();
    long currentUnusedStorage = currentStorageLimit - accountCapsule.getStorageUsage();

    if (bytes > currentUnusedStorage) {
      throw new ContractValidateException(
          "bytes must be less than currentUnusedStorage[" + currentUnusedStorage + "]");
    }

    long quantity = storageMarket.trySellStorage(bytes);
    if (quantity <= 1_000_000L) {
      throw new ContractValidateException(
          "quantity must be larger than 1TRX,current quantity[" + quantity + "]");
    }

    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(SellStorageContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }

}

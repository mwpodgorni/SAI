package loanbroker.gui;



import bank.model.BankInterestReply;
import bank.model.BankInterestRequest;
import loanclient.model.LoanRequest;

/**
 * This class is an item/line for a ListViewLine. It makes it possible to put both BankInterestRequest and BankInterestReply object in one item in a ListViewLine.
 */
class ListViewLine  {
	
	private LoanRequest loanRequest;
	private BankInterestReply bankInterestReply;
	
	public ListViewLine(LoanRequest loanRequest) {
		setLoanRequest(loanRequest);
		setBankInterestReply(null);
	}	
	
	public LoanRequest getLoanRequest() {
		return loanRequest;
	}
	
	private void setLoanRequest(LoanRequest loanRequest) {
		this.loanRequest = loanRequest;
	}

	public void setBankInterestReply(BankInterestReply bankInterestReply) {
		this.bankInterestReply = bankInterestReply;
	}

    /**
     * This method defines how one line is shown in the ListViewLine.
     * @return
     *  a) if BankInterestReply is null, then this item will be shown as "loanRequest.toString ---> waiting for loan reply..."
     *  b) if BankInterestReply is not null, then this item will be shown as "loanRequest.toString ---> bankInterestReply.toString"
     */
	@Override
	public String toString() {
	   return loanRequest.toString() + "  ||  " + ((bankInterestReply !=null)? bankInterestReply.toString():"waiting...");
	}
	
}

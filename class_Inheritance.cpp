#include <iostream>
#include <memory>

class IFilter {
  public:
    IFilter(){};
    IFilter(int in);
    virtual ~IFilter();
    virtual void start() = 0;
  private:
    int type;
};
IFilter::IFilter(int in) {
    std::cout << "IFilter(IIII) \n";
    type = in;
}
IFilter::~IFilter() {
    std::cout << "~IFilter(IIII) \n";
}


class Filter : public IFilter {
  public:
    Filter(){};
    Filter(int in);
    virtual ~Filter();
    virtual void start() override;
    void start1();
  protected:
    int filter_type;
};
Filter::Filter(int in) {
    std::cout << "Filter(D111) \n";
    filter_type = in;
    std::cout << "Filter()::" << "in=" << in <<"\n";
}
Filter::~Filter() {
    std::cout << "~Filter(D111) \n";
}
void Filter::start() {
    std::cout << "Filter::start(D111):" << "filter_type=" << filter_type <<"\n";
}
void Filter::start1() {
    std::cout << "Filter::start1111(D111):" << "filter_type=" << filter_type <<"\n";
}

class AFilter: public Filter {
  public:
    AFilter(){};
    AFilter(int in);
    ~AFilter();
    void start();
  private:
    int aFilter_type;    
};
AFilter::AFilter(int in) {
    std::cout << "AFilter(D222) \n";
    filter_type = in -1;
    aFilter_type = in;
    std::cout << "AFilter()::" << "in=" << in <<"\n";
}
AFilter::~AFilter() {
    std::cout << "~AFilter(D222) \n";
}
void AFilter::start() {
    Filter::start();
    std::cout << "AFilter::start(D222):" << "filter_type=" << filter_type <<"\n";
    std::cout << "AFilter::start(D222):" << "aFilter_type=" << aFilter_type <<"\n";
}

class BFilter: public Filter {
  public:
    BFilter(){};
    BFilter(int in);
    ~BFilter();
    //virtual void start() override;
  private:
    int bFilter_type;    
};
BFilter::BFilter(int in) {
    std::cout << "BFilter(D222) \n";
    bFilter_type = in;
    std::cout << "BFilter()::" << "in=" << in <<"\n";
}
BFilter::~BFilter() {
    std::cout << "~BFilter(D222) \n";
}
/*
void BFilter::start() {
    std::cout << "BFilter::start(D222):" << "bFilter_type=" << bFilter_type <<"\n";
}*/

int main()
{
    //Filter* aFilter = new AFilter(222);
    std::unique_ptr<Filter> aFilter = std::make_unique<AFilter>(3);
    aFilter->start();
    aFilter->start1();
    
    //Filter* bFilter = new BFilter(222);
    std::unique_ptr<Filter> bFilter = std::make_unique<BFilter>(4);
    bFilter->start();
    
    //Filter* filter = new Filter(111);
    std::unique_ptr<Filter> filter = std::make_unique<Filter>();
    filter->start();
    //delete aFilter;
    //delete bFilter;
    //delete filter;
}
